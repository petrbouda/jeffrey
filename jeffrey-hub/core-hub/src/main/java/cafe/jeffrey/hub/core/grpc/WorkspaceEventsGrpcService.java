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

import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.api.v1.*;
import cafe.jeffrey.hub.core.workspace.WorkspaceEventCallbacks;
import cafe.jeffrey.hub.core.workspace.WorkspaceEventPage;
import cafe.jeffrey.hub.core.workspace.WorkspaceEventReader;
import cafe.jeffrey.hub.core.workspace.WorkspaceEventStreamingManager;
import cafe.jeffrey.hub.core.workspace.WorkspaceEventSubscription;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventType;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WorkspaceEventsGrpcService extends WorkspaceEventsServiceGrpc.WorkspaceEventsServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceEventsGrpcService.class);
    private static final int DEFAULT_LIMIT = 100;
    private static final int DEFAULT_STREAM_BATCH_SIZE = 500;

    private final WorkspaceEventReader workspaceEventReader;
    private final WorkspaceEventStreamingManager streamingManager;

    public WorkspaceEventsGrpcService(
            WorkspaceEventReader workspaceEventReader,
            WorkspaceEventStreamingManager streamingManager) {

        this.workspaceEventReader = workspaceEventReader;
        this.streamingManager = streamingManager;
    }

    @Override
    public void getWorkspaceEvents(
            GetWorkspaceEventsRequest request,
            StreamObserver<GetWorkspaceEventsResponse> responseObserver) {

        GrpcUnary.respond(responseObserver, () -> {
            String workspaceId = request.getWorkspaceId();
            int limit = (request.hasLimit() && request.getLimit() > 0) ? request.getLimit() : DEFAULT_LIMIT;

            long totalCount = workspaceEventReader.count(workspaceId);

            // When filtering by type, fetch all events and apply the limit after filtering
            // so callers asking for the latest N of a specific type get a useful result.
            // Without a filter, push the limit to the queue for the cheap path.
            int fetchLimit = request.hasEventType() ? -1 : limit;
            Stream<WorkspaceEvent> eventStream = workspaceEventReader.findAll(workspaceId, fetchLimit).stream();

            if (request.hasEventType()) {
                WorkspaceEventType filterType = parseEventType(request.getEventType());
                eventStream = eventStream
                        .filter(event -> event.eventType() == filterType)
                        .limit(limit);
            }

            List<WorkspaceEventInfo> events = eventStream
                    .map(WorkspaceEventsGrpcService::toProto)
                    .toList();

            LOG.debug("Fetched workspace events via gRPC: workspaceId={} count={} total={} limit={}",
                    workspaceId, events.size(), totalCount, limit);

            return GetWorkspaceEventsResponse.newBuilder()
                    .addAllEvents(events)
                    .setTotalCount(totalCount)
                    .build();
        });
    }

    @Override
    public void streamWorkspaceEvents(
            StreamWorkspaceEventsRequest request,
            StreamObserver<WorkspaceEventBatch> observer) {

        // Catch-up reads pages as fast as the database can serve them, so the producer must be
        // pausable. The gate must be attached here, on the handler thread, before this method
        // returns — gRPC rejects setOnReadyHandler once the observer has been handed back.
        ServerCallStreamObserver<WorkspaceEventBatch> serverObserver =
                (ServerCallStreamObserver<WorkspaceEventBatch>) observer;
        ReadyGate gate = ReadyGate.attach(serverObserver);

        try {
            WorkspaceEventSubscription subscription = toSubscription(request);

            var callbacks = new WorkspaceEventCallbacks(
                    page -> GrpcStreams.sendWithBackpressure(serverObserver, gate, toProto(page)),
                    observer::onCompleted,
                    t -> observer.onError(GrpcExceptions.internal(t)));

            String subscriptionId = streamingManager.subscribe(subscription, callbacks);

            GrpcStreams.unsubscribeOnDisconnect("workspace-events", subscription,
                    () -> streamingManager.unsubscribe(subscriptionId));
        } catch (WorkspaceEventStreamingManager.TooManySubscriptionsException e) {
            observer.onError(GrpcExceptions.resourceExhausted(e.getMessage()));
        } catch (Exception e) {
            // toStatus is the central mapper: it passes an already-statused exception through
            // (parseEventType throws one), maps IllegalArgumentException from the subscription's
            // own validation to INVALID_ARGUMENT, and logs anything else as INTERNAL.
            observer.onError(GrpcExceptions.toStatus(e));
        }
    }

    /**
     * Maps the request onto the domain subscription, applying server defaults. An empty
     * {@code event_types} list means all types — unlike the JFR streaming service, which
     * rejects it.
     */
    private static WorkspaceEventSubscription toSubscription(StreamWorkspaceEventsRequest request) {
        Set<WorkspaceEventType> eventTypes = request.getEventTypesList().stream()
                .map(WorkspaceEventsGrpcService::parseEventType)
                .collect(Collectors.toUnmodifiableSet());

        int batchSize = (request.hasBatchSize() && request.getBatchSize() > 0)
                ? request.getBatchSize()
                : DEFAULT_STREAM_BATCH_SIZE;

        return new WorkspaceEventSubscription(
                request.getWorkspaceId(),
                request.hasFromOffset() ? request.getFromOffset() : 0L,
                eventTypes,
                request.getSendEmptyBatches(),
                batchSize);
    }

    private static WorkspaceEventBatch toProto(WorkspaceEventPage page) {
        return WorkspaceEventBatch.newBuilder()
                .addAllEvents(page.events().stream()
                        .map(WorkspaceEventsGrpcService::toProto)
                        .toList())
                .setLastOffset(page.lastOffset())
                .setCaughtUp(page.caughtUp())
                .build();
    }

    private static WorkspaceEventType parseEventType(String raw) {
        try {
            return WorkspaceEventType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw GrpcExceptions.invalidArgument("Invalid event type: " + raw);
        }
    }

    private static WorkspaceEventInfo toProto(WorkspaceEvent event) {
        return WorkspaceEventInfo.newBuilder()
                .setEventId(event.eventId() != null ? event.eventId() : 0)
                .setOriginEventId(ProtoMappers.orEmpty(event.originEventId()))
                .setProjectId(ProtoMappers.orEmpty(event.projectId()))
                .setWorkspaceRefId(ProtoMappers.orEmpty(event.workspaceRefId()))
                .setEventType(event.eventType() != null ? event.eventType().name() : "")
                .setContent(ProtoMappers.orEmpty(event.content()))
                .setOriginCreatedAt(event.originCreatedAt() != null ? event.originCreatedAt().toEpochMilli() : 0)
                .setCreatedAt(event.createdAt() != null ? event.createdAt().toEpochMilli() : 0)
                .setCreatedBy(ProtoMappers.orEmpty(event.createdBy()))
                .build();
    }
}
