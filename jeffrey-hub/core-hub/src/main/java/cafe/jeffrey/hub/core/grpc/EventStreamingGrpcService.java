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

import io.grpc.Context;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.api.v1.EventBatch;
import cafe.jeffrey.hub.api.v1.EventStreamingServiceGrpc;
import cafe.jeffrey.hub.api.v1.LiveStreamingRequest;
import cafe.jeffrey.hub.api.v1.ReplayStreamingRequest;
import cafe.jeffrey.hub.core.HubJeffreyDirs;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.core.streaming.*;
import cafe.jeffrey.hub.persistence.api.SessionWithRepository;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
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

    private final HubJeffreyDirs jeffreyDirs;
    private final HubPlatformRepositories platformRepositories;
    private final LiveStreamingManager liveStreamingManager;
    private final ReplayStreamingManager replayStreamingManager;
    private final RepositoryStorage.Factory repositoryStorageFactory;

    public EventStreamingGrpcService(
            HubJeffreyDirs jeffreyDirs,
            HubPlatformRepositories platformRepositories,
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
                    resolveValidatedSession(sessionId, request.getEventTypesList(), observer);
            if (sessionOpt.isEmpty()) {
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

            unsubscribeOnDisconnect("live", subscription,
                    () -> liveStreamingManager.unsubscribe(subscriptionId));
        } catch (Exception e) {
            LOG.error("Failed to start live streaming: sessionId={}", sessionId, e);
            observer.onError(GrpcExceptions.internal(e));
        }
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
            Optional<SessionWithRepository> sessionOpt =
                    resolveValidatedSession(sessionId, request.getEventTypesList(), observer);
            if (sessionOpt.isEmpty()) {
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
                    batch -> sendWithBackpressure(serverObserver, gate, batch),
                    observer::onCompleted,
                    t -> observer.onError(GrpcExceptions.internal(t)));

            String replayId = replayStreamingManager.subscribe(replaySubscription, callbacks);

            unsubscribeOnDisconnect("replay", replaySubscription,
                    () -> replayStreamingManager.unsubscribe(replayId));
        } catch (IllegalArgumentException e) {
            observer.onError(GrpcExceptions.invalidArgument(e.getMessage()));
        } catch (Exception e) {
            LOG.error("Failed to start replay streaming: sessionId={}", sessionId, e);
            observer.onError(GrpcExceptions.internal(e));
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

    /**
     * Unsubscribes the streaming subscription when the client disconnects (gRPC context
     * cancellation), releasing the subscriber and its resources.
     */
    private static void unsubscribeOnDisconnect(String streamKind, Object subscription, Runnable unsubscribe) {
        Context.current().addListener(_ -> {
            LOG.info("Client disconnected, closing {} stream: subscription={}", streamKind, subscription);
            unsubscribe.run();
        }, Runnable::run);
    }

    /**
     * Delivers a batch to the client, pausing on the streaming thread until the channel
     * is ready to accept more data. Batches produced after cancellation are dropped.
     */
    private static void sendWithBackpressure(
            ServerCallStreamObserver<EventBatch> observer, ReadyGate gate, EventBatch batch) {
        try {
            gate.awaitReady();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        if (!gate.isCancelled()) {
            observer.onNext(batch);
        }
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
