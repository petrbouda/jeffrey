/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package cafe.jeffrey.hub.core.grpc;

import cafe.jeffrey.hub.api.v1.EventBatch;
import cafe.jeffrey.hub.api.v1.EventStreamingServiceGrpc;
import cafe.jeffrey.hub.api.v1.ReplayStreamingRequest;
import cafe.jeffrey.hub.core.HubJeffreyDirs;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.core.streaming.ReplayScopeNotFoundException;
import cafe.jeffrey.hub.core.streaming.ReplayStreamSubscription;
import cafe.jeffrey.hub.core.streaming.ReplayStreamingManager;
import cafe.jeffrey.hub.core.streaming.ScopedReplaySource;
import cafe.jeffrey.hub.core.streaming.StreamingCallbacks;
import cafe.jeffrey.hub.core.streaming.StreamingWindow;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
import cafe.jeffrey.hub.persistence.api.SessionWithRepository;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Replays JFR events from finished recording files (.jfr/.jfr.lz4).
 * Supports legacy session lookup and workspace/project-scoped read-only queries.
 */
public class EventStreamingGrpcService extends EventStreamingServiceGrpc.EventStreamingServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(EventStreamingGrpcService.class);

    private static final String NO_EVENT_TYPES = "At least one event type must be specified";
    private static final String SCOPE_REQUIRED = "Both workspace_id and project_id are required for scoped replay";
    private static final String SESSION_NOT_FOUND = "Session not found: ";
    private static final String NO_RECORDING_FILES = "No recording files found for session: ";

    private final HubJeffreyDirs jeffreyDirs;
    private final HubPlatformRepositories platformRepositories;
    private final ReplayStreamingManager replayStreamingManager;
    private final RepositoryStorage.Factory repositoryStorageFactory;
    private final ScopedReplaySource scopedReplaySource;

    public EventStreamingGrpcService(
            HubJeffreyDirs jeffreyDirs,
            HubPlatformRepositories platformRepositories,
            ReplayStreamingManager replayStreamingManager,
            RepositoryStorage.Factory repositoryStorageFactory,
            ScopedReplaySource scopedReplaySource) {

        this.jeffreyDirs = jeffreyDirs;
        this.platformRepositories = platformRepositories;
        this.replayStreamingManager = replayStreamingManager;
        this.repositoryStorageFactory = repositoryStorageFactory;
        this.scopedReplaySource = scopedReplaySource;
    }

    @Override
    public void scopedReplayStreaming(ReplayStreamingRequest request, StreamObserver<EventBatch> observer) {
        if (request.getWorkspaceId().isBlank() || request.getProjectId().isBlank()) {
            observer.onError(GrpcExceptions.invalidArgument("Scoped replay requires workspace_id and project_id"));
            return;
        }
        replayStreaming(request, observer);
    }

    @Override
    public void replayStreaming(ReplayStreamingRequest request, StreamObserver<EventBatch> observer) {
        String sessionId = request.getSessionId();

        // Replay produces batches as fast as the files can be read, so the producer must be
        // paused while the client is slow — otherwise gRPC buffers every batch in memory.
        // The gate must be attached here, on the handler thread, before this method returns.
        ServerCallStreamObserver<EventBatch> serverObserver = (ServerCallStreamObserver<EventBatch>) observer;
        ReadyGate gate = ReadyGate.attach(serverObserver);

        try {
            if (request.getEventTypesCount() == 0) {
                throw new IllegalArgumentException(NO_EVENT_TYPES);
            }
            StreamingWindow window = resolveStreamingWindow(request);
            Set<String> eventTypes = new HashSet<>(request.getEventTypesList());
            boolean scoped = request.hasWorkspaceId() || request.hasProjectId();
            ReplayStreamSubscription replaySubscription = scoped
                    ? resolveScopedSubscription(request, eventTypes, window)
                    : resolveLegacySubscription(sessionId, eventTypes, window);

            var callbacks = new StreamingCallbacks(
                    batch -> GrpcStreams.sendWithBackpressure(serverObserver, gate, batch),
                    observer::onCompleted,
                    t -> observer.onError(GrpcExceptions.internal(t)));

            String replayId = replayStreamingManager.subscribe(replaySubscription, callbacks);

            GrpcStreams.unsubscribeOnDisconnect("replay", replaySubscription,
                    () -> replayStreamingManager.unsubscribe(replayId));
        } catch (Exception e) {
            // One mapping for the whole server: a scope that does not exist is NOT_FOUND here for the
            // same reason it is on the unary activity calls, and anything unrecognised is logged and
            // reported as INTERNAL by the mapper itself.
            LOG.debug("Replay streaming rejected: sessionId={}", sessionId, e);
            observer.onError(GrpcExceptions.toStatus(e));
        }
    }

    // ========== Helpers ==========

    /**
     * Resolves the session inside its workspace and project. A session that resolves but has no
     * finished recording file yet is a valid, empty replay — the subscriber acknowledges the scope,
     * reports terminal coverage and completes with zero events — so that replay and the activity
     * scan, which admits the same scope, agree about what that session is. Only a workspace, project
     * or session that does not resolve is NOT_FOUND, thrown by the source as
     * {@link ReplayScopeNotFoundException}.
     */
    private ReplayStreamSubscription resolveScopedSubscription(
            ReplayStreamingRequest request, Set<String> eventTypes, StreamingWindow window) {

        if (request.getWorkspaceId().isBlank() || request.getProjectId().isBlank()) {
            throw new IllegalArgumentException(SCOPE_REQUIRED);
        }
        return scopedReplaySource.resolve(
                request.getWorkspaceId(),
                request.getProjectId(),
                request.getSessionId(),
                eventTypes,
                window);
    }

    /**
     * Looks the session up by id alone, across every project, the way callers older than the
     * scoped RPC expect. Keeps answering NOT_FOUND for a session with no recording files, because
     * legacy replay carries no coverage status that could say the empty answer was a complete one.
     */
    private ReplayStreamSubscription resolveLegacySubscription(
            String sessionId, Set<String> eventTypes, StreamingWindow window) {

        SessionWithRepository session = platformRepositories.findSessionWithRepositoryById(sessionId)
                .orElseThrow(() -> new ReplayScopeNotFoundException(SESSION_NOT_FOUND + sessionId));
        RepositoryStorage storage = repositoryStorageFactory.apply(session.projectInfo());
        List<Path> recordingFiles = storage.finishedChunks(sessionId);
        if (recordingFiles.isEmpty()) {
            throw new ReplayScopeNotFoundException(NO_RECORDING_FILES + sessionId);
        }
        return new ReplayStreamSubscription(sessionId, recordingFiles, eventTypes, window, jeffreyDirs.temp());
    }

    private static StreamingWindow resolveStreamingWindow(ReplayStreamingRequest request) {
        Instant startTime = request.hasStartTime()
                ? Instant.ofEpochMilli(request.getStartTime())
                : null;
        Instant endTime = request.hasEndTime()
                ? Instant.ofEpochMilli(request.getEndTime())
                : null;

        return new StreamingWindow(startTime, endTime);
    }
}
