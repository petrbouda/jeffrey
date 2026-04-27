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

package cafe.jeffrey.server.core.grpc;

import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.server.api.v1.EventBatch;
import cafe.jeffrey.server.api.v1.EventStreamingServiceGrpc;
import cafe.jeffrey.server.api.v1.LiveStreamingRequest;
import cafe.jeffrey.server.api.v1.ReplayStreamingRequest;
import cafe.jeffrey.server.core.ServerJeffreyDirs;
import cafe.jeffrey.server.core.project.repository.RepositoryStorage;
import cafe.jeffrey.server.core.streaming.*;
import cafe.jeffrey.server.persistence.api.SessionWithRepository;
import cafe.jeffrey.server.persistence.api.ServerPlatformRepositories;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;

import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/**
 * gRPC service providing two modes of JFR event access:
 * <ul>
 *   <li><b>Live streaming</b> — subscribes to a session's streaming repository for real-time events</li>
 *   <li><b>Replay streaming</b> — reads dumped recording files (.jfr/.jfr.lz4) for historical events</li>
 * </ul>
 */
public class EventStreamingGrpcService extends EventStreamingServiceGrpc.EventStreamingServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(EventStreamingGrpcService.class);

    private final ServerJeffreyDirs jeffreyDirs;
    private final ServerPlatformRepositories platformRepositories;
    private final LiveStreamingManager liveStreamingManager;
    private final ReplayStreamingManager replayStreamingManager;
    private final RepositoryStorage.Factory repositoryStorageFactory;

    public EventStreamingGrpcService(
            ServerJeffreyDirs jeffreyDirs,
            ServerPlatformRepositories platformRepositories,
            LiveStreamingManager liveStreamingManager,
            ReplayStreamingManager replayStreamingManager,
            RepositoryStorage.Factory repositoryStorageFactory) {

        this.jeffreyDirs = jeffreyDirs;
        this.platformRepositories = platformRepositories;
        this.liveStreamingManager = liveStreamingManager;
        this.replayStreamingManager = replayStreamingManager;
        this.repositoryStorageFactory = repositoryStorageFactory;
    }

    @Override
    public void liveStreaming(LiveStreamingRequest request, StreamObserver<EventBatch> observer) {
        String sessionId = request.getSessionId();

        try {
            Optional<SessionWithRepository> sessionOpt =
                    platformRepositories.findSessionWithRepositoryById(sessionId);
            if (sessionOpt.isEmpty()) {
                observer.onError(GrpcExceptions.notFound("Session not found: " + sessionId));
                return;
            }

            if (request.getEventTypesList().isEmpty()) {
                observer.onError(GrpcExceptions.invalidArgument("At least one event type must be specified"));
                return;
            }

            Path streamingRepoPath = SessionPaths.resolveStreamingRepo(jeffreyDirs, sessionOpt.get());
            if (!FileSystemUtils.isDirectory(streamingRepoPath)) {
                observer.onError(GrpcExceptions.unavailable("Session repository for streaming is not available: " + sessionId));
                return;
            }

            LiveStreamSubscription subscription = new LiveStreamSubscription(
                    sessionId,
                    streamingRepoPath,
                    new HashSet<>(request.getEventTypesList()),
                    request.getSendEmptyBatches());

            var callbacks = new StreamingCallbacks(
                    observer::onNext,
                    observer::onCompleted,
                    t -> observer.onError(GrpcExceptions.internal(t)));

            String subscriptionId = liveStreamingManager.subscribe(subscription, callbacks);

            Context.current().addListener(_ -> {
                LOG.info("Client disconnected, closing live stream: subscription={}", subscription);
                liveStreamingManager.unsubscribe(subscriptionId);
            }, Runnable::run);
        } catch (Exception e) {
            LOG.error("Failed to start live streaming: sessionId={}", sessionId, e);
            observer.onError(GrpcExceptions.internal(e));
        }
    }

    @Override
    public void replayStreaming(ReplayStreamingRequest request, StreamObserver<EventBatch> observer) {
        String sessionId = request.getSessionId();

        try {
            Optional<SessionWithRepository> sessionOpt =
                    platformRepositories.findSessionWithRepositoryById(sessionId);
            if (sessionOpt.isEmpty()) {
                observer.onError(GrpcExceptions.notFound("Session not found: " + sessionId));
                return;
            }

            if (request.getEventTypesList().isEmpty()) {
                observer.onError(GrpcExceptions.invalidArgument("At least one event type must be specified"));
                return;
            }

            StreamingWindow window = resolveStreamingWindow(request);

            RepositoryStorage storage = repositoryStorageFactory.apply(sessionOpt.get().projectInfo());
            List<Path> recordingFiles = storage.recordings(sessionId, null);
            if (recordingFiles.isEmpty()) {
                observer.onError(GrpcExceptions.notFound("No recording files found for session: " + sessionId));
                return;
            }

            ReplayStreamSubscription replaySubscription = new ReplayStreamSubscription(
                    sessionId,
                    recordingFiles,
                    new HashSet<>(request.getEventTypesList()),
                    window, jeffreyDirs.temp());

            var callbacks = new StreamingCallbacks(
                    observer::onNext,
                    observer::onCompleted,
                    t -> observer.onError(GrpcExceptions.internal(t)));

            String replayId = replayStreamingManager.subscribe(replaySubscription, callbacks);

            Context.current().addListener(_ -> {
                LOG.info("Client disconnected, closing replay stream: subscription={}", replaySubscription);
                replayStreamingManager.unsubscribe(replayId);
            }, Runnable::run);
        } catch (IllegalArgumentException e) {
            observer.onError(GrpcExceptions.invalidArgument(e.getMessage()));
        } catch (Exception e) {
            LOG.error("Failed to start replay streaming: sessionId={}", sessionId, e);
            observer.onError(GrpcExceptions.internal(e));
        }
    }

    // ========== Helpers ==========

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
