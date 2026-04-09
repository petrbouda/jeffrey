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

package pbouda.jeffrey.server.core.grpc;

import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.server.api.v1.EventBatch;
import pbouda.jeffrey.server.api.v1.EventStreamingServiceGrpc;
import pbouda.jeffrey.server.api.v1.SubscribeEventsRequest;
import pbouda.jeffrey.server.core.ServerJeffreyDirs;
import pbouda.jeffrey.server.core.streaming.EventStreamSubscription;
import pbouda.jeffrey.server.core.streaming.EventStreamingSubscriptionManager;
import pbouda.jeffrey.server.core.streaming.SessionPaths;
import pbouda.jeffrey.server.core.streaming.EventStreamSubscriber;
import pbouda.jeffrey.server.persistence.model.SessionWithRepository;
import pbouda.jeffrey.server.persistence.repository.ServerPlatformRepositories;
import pbouda.jeffrey.shared.common.filesystem.FileSystemUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * gRPC service that allows clients to subscribe to live JFR events from a session's
 * streaming repository. Each subscriber gets their own {@link EventStreamSubscriber}
 * with independent start time and event type filtering.
 */
public class EventStreamingGrpcService extends EventStreamingServiceGrpc.EventStreamingServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(EventStreamingGrpcService.class);

    private final ServerJeffreyDirs jeffreyDirs;
    private final ServerPlatformRepositories platformRepositories;
    private final EventStreamingSubscriptionManager subscriptionManager;
    private final Clock clock;

    public EventStreamingGrpcService(
            ServerJeffreyDirs jeffreyDirs,
            ServerPlatformRepositories platformRepositories,
            EventStreamingSubscriptionManager subscriptionManager,
            Clock clock) {

        this.jeffreyDirs = jeffreyDirs;
        this.platformRepositories = platformRepositories;
        this.subscriptionManager = subscriptionManager;
        this.clock = clock;
    }

    @Override
    public void subscribeEvents(SubscribeEventsRequest request, StreamObserver<EventBatch> observer) {
        String sessionId = request.getSessionId();

        try {
            Optional<SessionWithRepository> session =
                    platformRepositories.findSessionWithRepositoryById(sessionId);

            if (session.isEmpty()) {
                observer.onError(Status.NOT_FOUND
                        .withDescription("Session not found: " + sessionId)
                        .asRuntimeException());
                return;
            }

            Path streamingRepoPath = SessionPaths.resolveStreamingRepo(jeffreyDirs, session.get());
            if (!FileSystemUtils.isDirectory(streamingRepoPath)) {
                observer.onError(Status.UNAVAILABLE
                        .withDescription("Session repository for streaming is not available: " + sessionId)
                        .asRuntimeException());
                return;
            }

            if (request.getEventTypesList().isEmpty()) {
                observer.onError(Status.INVALID_ARGUMENT
                        .withDescription("At least one event type must be specified")
                        .asRuntimeException());
                return;
            }

            Instant now = clock.instant();
            Instant requestedStart = request.hasStartTime()
                    ? Instant.ofEpochMilli(request.getStartTime())
                    : null;
            Instant requestedEnd = request.hasEndTime()
                    ? Instant.ofEpochMilli(request.getEndTime())
                    : null;

            if (request.getContinuous() && requestedEnd != null) {
                observer.onError(Status.INVALID_ARGUMENT
                        .withDescription("endTime must not be set when continuous=true; use continuous mode without an endTime or set continuous=false")
                        .asRuntimeException());
                return;
            }
            if (requestedEnd != null && requestedEnd.isAfter(now)) {
                observer.onError(Status.INVALID_ARGUMENT
                        .withDescription("endTime must not be in the future; for future endTime, switch to continuous mode")
                        .asRuntimeException());
                return;
            }
            if (requestedStart != null && requestedEnd != null && !requestedStart.isBefore(requestedEnd)) {
                observer.onError(Status.INVALID_ARGUMENT
                        .withDescription("startTime must be strictly before endTime")
                        .asRuntimeException());
                return;
            }

            Set<String> eventTypes = new HashSet<>(request.getEventTypesList());
            // When not continuous, set endTime to bound the stream
            // (use explicit endTime if provided, otherwise current time)
            Instant endTime;
            if (request.getContinuous()) {
                endTime = null;
            } else if (requestedEnd != null) {
                endTime = requestedEnd;
            } else {
                endTime = now;
            }

            EventStreamSubscription subscription = new EventStreamSubscription(
                    sessionId,
                    streamingRepoPath,
                    eventTypes,
                    requestedStart,
                    endTime,
                    request.getSendEmptyBatches());

            String subscriptionId = subscriptionManager.subscribe(
                    subscription,
                    observer::onNext,
                    observer::onCompleted,
                    t -> observer.onError(Status.INTERNAL.withDescription(t.getMessage()).asRuntimeException()));

            // Clean up on client disconnect
            Context.current().addListener(_ -> {
                LOG.info("Client disconnected, closing subscriber stream: sessionId={}", sessionId);
                subscriptionManager.unsubscribe(subscriptionId);
            }, Runnable::run);

        } catch (IOException e) {
            LOG.error("Failed to open streaming repository: sessionId={}", sessionId, e);
            observer.onError(Status.INTERNAL
                    .withDescription("Failed to open streaming repository: " + e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            LOG.error("Failed to subscribe to events: sessionId={}", sessionId, e);
            observer.onError(Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }
}
