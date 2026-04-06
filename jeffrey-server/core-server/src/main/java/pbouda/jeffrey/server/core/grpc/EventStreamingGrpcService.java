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
import pbouda.jeffrey.server.core.streaming.EventStreamingSubscriptionManager;
import pbouda.jeffrey.server.core.streaming.SessionPaths;
import pbouda.jeffrey.server.core.streaming.SubscriberEventStream;
import pbouda.jeffrey.server.persistence.repository.ProjectRepositoryRepository;
import pbouda.jeffrey.server.persistence.repository.ServerPlatformRepositories;
import pbouda.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import pbouda.jeffrey.shared.common.model.RepositoryInfo;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * gRPC service that allows clients to subscribe to live JFR events from a session's
 * streaming repository. Each subscriber gets their own {@link SubscriberEventStream}
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
    public void subscribeEvents(SubscribeEventsRequest request, StreamObserver<EventBatch> responseObserver) {
        String sessionId = request.getSessionId();
        String projectId = request.getProjectId();

        try {
            ProjectRepositoryRepository repoRepository =
                    platformRepositories.newProjectRepositoryRepository(projectId);

            List<RepositoryInfo> repos = repoRepository.getAll();
            if (repos.isEmpty()) {
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("No repository found for project: " + projectId)
                        .asRuntimeException());
                return;
            }

            RepositoryInfo repositoryInfo = repos.getFirst();
            ProjectInstanceSessionInfo sessionInfo = repoRepository.findSessionById(sessionId)
                    .orElse(null);

            if (sessionInfo == null) {
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("Session not found: " + sessionId)
                        .asRuntimeException());
                return;
            }

            if (request.getEventTypesList().isEmpty()) {
                responseObserver.onError(Status.INVALID_ARGUMENT
                        .withDescription("At least one event type must be specified")
                        .asRuntimeException());
                return;
            }

            Path sessionPath = SessionPaths.resolve(jeffreyDirs, repositoryInfo, sessionInfo);
            Set<String> eventTypes = new HashSet<>(request.getEventTypesList());
            Instant startTime = request.hasStartTime()
                    ? Instant.ofEpochMilli(request.getStartTime())
                    : null;
            // When not continuous, set endTime to bound the stream
            // (use explicit endTime if provided, otherwise current time)
            Instant endTime;
            if (request.getContinuous()) {
                endTime = null;
            } else if (request.hasEndTime()) {
                endTime = Instant.ofEpochMilli(request.getEndTime());
            } else {
                endTime = clock.instant();
            }

            SubscriberEventStream stream = subscriptionManager.subscribe(
                    sessionId,
                    sessionPath,
                    eventTypes,
                    startTime,
                    endTime,
                    request.getSendEmptyBatches(),
                    responseObserver);

            // Clean up on client disconnect
            Context.current().addListener(_ -> {
                LOG.info("Client disconnected, closing subscriber stream: sessionId={}", sessionId);
                subscriptionManager.unsubscribe(sessionId, stream);
            }, Runnable::run);

        } catch (IOException e) {
            LOG.error("Failed to open streaming repository: sessionId={}", sessionId, e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to open streaming repository: " + e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            LOG.error("Failed to subscribe to events: sessionId={}", sessionId, e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }
}
