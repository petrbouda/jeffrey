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
import java.util.Optional;

/**
 * Replays JFR events from finished recording files (.jfr/.jfr.lz4).
 * Supports legacy session lookup and workspace/project-scoped read-only queries.
 */
public class EventStreamingGrpcService extends EventStreamingServiceGrpc.EventStreamingServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(EventStreamingGrpcService.class);

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
            boolean scoped = request.hasWorkspaceId() || request.hasProjectId();
            if (request.getEventTypesCount() == 0) {
                throw new IllegalArgumentException("At least one event type must be specified");
            }
            StreamingWindow window = resolveStreamingWindow(request);
            List<Path> recordingFiles;
            if (scoped) {
                if (request.getWorkspaceId().isBlank() || request.getProjectId().isBlank()) {
                    throw new IllegalArgumentException("Both workspace_id and project_id are required for scoped replay");
                }
                recordingFiles = scopedReplaySource
                        .resolve(
                                request.getWorkspaceId(),
                                request.getProjectId(),
                                sessionId,
                                new HashSet<>(request.getEventTypesList()),
                                window)
                        .recordingFiles();
            } else {
                Optional<SessionWithRepository> sessionOpt =
                        resolveValidatedSession(sessionId, request.getEventTypesList(), observer);
                if (sessionOpt.isEmpty()) {
                    return;
                }
                RepositoryStorage storage = repositoryStorageFactory.apply(sessionOpt.get().projectInfo());
                recordingFiles = storage.recordings(sessionId, null);
            }
            if (recordingFiles.isEmpty()) {
                observer.onError(GrpcExceptions.notFound("No recording files found for session: " + sessionId));
                return;
            }

            ReplayStreamSubscription replaySubscription = new ReplayStreamSubscription(
                    sessionId,
                    recordingFiles,
                    new HashSet<>(request.getEventTypesList()),
                    window, jeffreyDirs.temp(), scoped ? request.getWorkspaceId() : null,
                    scoped ? request.getProjectId() : null);

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
     * Resolves the session and validates the request. When the session is missing or the
     * event-type list is empty, the appropriate terminal error is already sent to the
     * observer and an empty Optional is returned — the caller just stops.
     */
    private Optional<SessionWithRepository> resolveValidatedSession(
            String sessionId, List<String> eventTypes, StreamObserver<EventBatch> observer) {

        Optional<SessionWithRepository> sessionOpt =
                platformRepositories.findSessionWithRepositoryById(sessionId);
        if (sessionOpt.isEmpty()) {
            observer.onError(GrpcExceptions.notFound("Session not found: " + sessionId));
            return Optional.empty();
        }

        if (eventTypes.isEmpty()) {
            observer.onError(GrpcExceptions.invalidArgument("At least one event type must be specified"));
            return Optional.empty();
        }

        return sessionOpt;
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
