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

package cafe.jeffrey.hub.client;

import cafe.jeffrey.microscope.grpc.client.*;

import io.grpc.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.api.v1.*;
import cafe.jeffrey.hub.client.dto.WorkspaceEventResponse;
import cafe.jeffrey.hub.client.dto.WorkspaceEventsBatch;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventType;

import java.io.Closeable;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class WorkspaceEventsClient implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceEventsClient.class);

    private final WorkspaceEventsServiceGrpc.WorkspaceEventsServiceBlockingStub stub;
    private final WorkspaceEventsServiceGrpc.WorkspaceEventsServiceStub asyncStub;
    private final Set<WorkspaceEventsSubscription> activeSubscriptions = ConcurrentHashMap.newKeySet();

    public WorkspaceEventsClient(GrpcHubConnection connection) {
        this.stub = WorkspaceEventsServiceGrpc.newBlockingStub(connection.getChannel());
        this.asyncStub = WorkspaceEventsServiceGrpc.newStub(connection.getChannel());
    }

    public WorkspaceEventsResult getEvents(String workspaceId, int limit) {
        GetWorkspaceEventsRequest.Builder builder = GetWorkspaceEventsRequest.newBuilder()
                .setWorkspaceId(workspaceId);
        if (limit > 0) {
            builder.setLimit(limit);
        }

        GetWorkspaceEventsResponse response = stub.getWorkspaceEvents(builder.build());

        LOG.debug("Fetched workspace events via gRPC: workspaceId={} count={} total={} limit={}",
                workspaceId, response.getEventsCount(), response.getTotalCount(), limit);

        List<WorkspaceEventResponse> events = response.getEventsList().stream()
                .map(WorkspaceEventsClient::toResponse)
                .toList();
        return new WorkspaceEventsResult(events, response.getTotalCount());
    }

    public record WorkspaceEventsResult(List<WorkspaceEventResponse> events, long totalCount) {
    }

    /**
     * Opens a long-lived subscription to a workspace's event stream. The server first replays
     * everything above {@code fromOffset}, then tails until the returned handle is cancelled or
     * the connection drops.
     *
     * @return a handle whose {@code cancel()} closes the stream
     */
    public WorkspaceEventsSubscription streamEvents(
            String workspaceId,
            WorkspaceEventsSubscribeRequest request,
            WorkspaceEventsStreamCallbacks callbacks) {

        StreamWorkspaceEventsRequest.Builder builder = StreamWorkspaceEventsRequest.newBuilder()
                .setWorkspaceId(workspaceId)
                .setSendEmptyBatches(request.sendEmptyBatches());

        if (request.fromOffset() > 0) {
            builder.setFromOffset(request.fromOffset());
        }
        request.eventTypes().stream()
                .map(Enum::name)
                .forEach(builder::addEventTypes);

        Context.CancellableContext cancellableContext = Context.current().withCancellation();
        var subscription = new WorkspaceEventsSubscription(cancellableContext, workspaceId);
        activeSubscriptions.add(subscription);

        cancellableContext.run(() -> asyncStub.streamWorkspaceEvents(builder.build(),
                new WorkspaceEventBatchStreamObserver(workspaceId, subscription, activeSubscriptions, callbacks)));

        LOG.info("Subscribed to workspace event stream: workspaceId={} fromOffset={}",
                workspaceId, request.fromOffset());
        return subscription;
    }

    /**
     * Subscription parameters.
     *
     * @param fromOffset exclusive resume point; {@code 0} starts from the oldest retained event
     * @param eventTypes types to receive; empty means all types
     * @param sendEmptyBatches whether the server should emit heartbeats while idle
     */
    public record WorkspaceEventsSubscribeRequest(
            long fromOffset,
            Set<WorkspaceEventType> eventTypes,
            boolean sendEmptyBatches) {

        public WorkspaceEventsSubscribeRequest {
            eventTypes = eventTypes == null ? Set.of() : Set.copyOf(eventTypes);
        }
    }

    /** Cancellation handle for an open subscription. */
    public record WorkspaceEventsSubscription(Context.CancellableContext context, String workspaceId) {

        public void cancel() {
            context.cancel(null);
        }
    }

    @Override
    public void close() {
        if (activeSubscriptions.isEmpty()) {
            return;
        }

        LOG.info("Cancelling active workspace event subscriptions: count={}", activeSubscriptions.size());
        for (WorkspaceEventsSubscription subscription : activeSubscriptions) {
            subscription.cancel();
        }
        activeSubscriptions.clear();
    }

    static WorkspaceEventsBatch toBatch(WorkspaceEventBatch proto) {
        return new WorkspaceEventsBatch(
                proto.getEventsList().stream()
                        .map(WorkspaceEventsClient::toResponse)
                        .toList(),
                proto.getLastOffset(),
                proto.getCaughtUp());
    }

    private static WorkspaceEventResponse toResponse(WorkspaceEventInfo proto) {
        return new WorkspaceEventResponse(
                proto.getEventId(),
                proto.getOriginEventId().isEmpty() ? null : proto.getOriginEventId(),
                proto.getProjectId().isEmpty() ? null : proto.getProjectId(),
                proto.getWorkspaceRefId().isEmpty() ? null : proto.getWorkspaceRefId(),
                proto.getEventType().isEmpty() ? null : WorkspaceEventType.valueOf(proto.getEventType()),
                proto.getContent().isEmpty() ? null : proto.getContent(),
                proto.getOriginCreatedAt(),
                proto.getCreatedAt(),
                proto.getCreatedBy().isEmpty() ? null : proto.getCreatedBy());
    }
}
