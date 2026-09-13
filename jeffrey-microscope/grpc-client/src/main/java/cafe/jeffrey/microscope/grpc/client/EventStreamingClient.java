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

package cafe.jeffrey.microscope.grpc.client;

import io.grpc.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.api.v1.EventStreamingServiceGrpc;
import cafe.jeffrey.hub.api.v1.ReplayStreamingRequest;
import cafe.jeffrey.hub.api.v1.EventActivityServiceGrpc;
import cafe.jeffrey.hub.api.v1.EventActivitySnapshot;
import cafe.jeffrey.hub.api.v1.StartActivityRequest;
import cafe.jeffrey.hub.api.v1.GetActivityRequest;
import cafe.jeffrey.hub.api.v1.CancelActivityRequest;
import java.util.concurrent.TimeUnit;

import java.io.Closeable;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * gRPC client for replaying JFR events from a Jeffrey Hub.
 * Uses an async stub for long-lived server-streaming subscriptions.
 */
public class EventStreamingClient implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(EventStreamingClient.class);

    private static final long ACTIVITY_RPC_TIMEOUT_SECONDS = 10;
    private final EventStreamingServiceGrpc.EventStreamingServiceStub stub;
    private final EventActivityServiceGrpc.EventActivityServiceBlockingStub activityStub;
    private final Set<EventStreamingSubscription> activeSubscriptions = ConcurrentHashMap.newKeySet();

    public EventStreamingClient(GrpcHubConnection connection) {
        this.stub = EventStreamingServiceGrpc.newStub(connection.getChannel());
        this.activityStub = EventActivityServiceGrpc.newBlockingStub(connection.getChannel());
    }

    /**
     * Replays historical JFR events from dumped recording files on the hub.
     *
     * @param request   replay parameters (session ID, event types, time range)
     * @param callbacks streaming lifecycle callbacks (onBatch, onComplete, onError)
     * @return a cancellation handle to stop the replay
     */
    public EventStreamingSubscription subscribeReplayStreaming(
            ReplaySubscriptionRequest request,
            StreamingCallbacks callbacks) {

        ReplayStreamingRequest.Builder requestBuilder = ReplayStreamingRequest.newBuilder()
                .setSessionId(request.sessionId())
                .addAllEventTypes(request.eventTypes())
                .setSendEmptyBatches(false);

        if (request.startTime() != null) {
            requestBuilder.setStartTime(request.startTime());
        }
        if (request.endTime() != null) {
            requestBuilder.setEndTime(request.endTime());
        }

        if (request.workspaceId() != null) {
            requestBuilder.setWorkspaceId(request.workspaceId());
        }
        if (request.projectId() != null) {
            requestBuilder.setProjectId(request.projectId());
        }
        String sessionId = request.sessionId();
        Context.CancellableContext cancellableContext = Context.current().withCancellation();
        EventStreamingSubscription subscription = new EventStreamingSubscription(cancellableContext, sessionId);
        activeSubscriptions.add(subscription);

        cancellableContext.run(() -> {
            var observer = new EventBatchStreamObserver(sessionId, subscription, activeSubscriptions, callbacks);
            if (request.workspaceId() != null || request.projectId() != null) {
                stub.scopedReplayStreaming(requestBuilder.build(), observer);
            } else {
                stub.replayStreaming(requestBuilder.build(), observer);
            }
        });

        LOG.info("Subscribed to replay stream: request={}", request);
        return subscription;
    }

    /** Starts an independent Hub scan; its lifetime is not limited by this RPC's deadline. */
    public EventActivitySnapshot startActivity(StartActivityRequest request) {
        return activityStub.withDeadlineAfter(ACTIVITY_RPC_TIMEOUT_SECONDS, TimeUnit.SECONDS).startActivity(request);
    }

    public EventActivitySnapshot getActivity(GetActivityRequest request) {
        return activityStub.withDeadlineAfter(ACTIVITY_RPC_TIMEOUT_SECONDS, TimeUnit.SECONDS).getActivity(request);
    }

    public EventActivitySnapshot cancelActivity(CancelActivityRequest request) {
        return activityStub.withDeadlineAfter(ACTIVITY_RPC_TIMEOUT_SECONDS, TimeUnit.SECONDS).cancelActivity(request);
    }

    @Override
    public void close() {
        if (activeSubscriptions.isEmpty()) {
            return;
        }

        LOG.info("Cancelling active event streaming subscriptions: count={}", activeSubscriptions.size());
        for (EventStreamingSubscription subscription : activeSubscriptions) {
            subscription.context().cancel(null);
            LOG.debug("Cancelled event streaming subscription: sessionId={}", subscription.sessionId());
        }
        activeSubscriptions.clear();
    }

    /**
     * Handle for cancelling an active event streaming subscription.
     */
    public record EventStreamingSubscription(Context.CancellableContext context, String sessionId) {
        public void cancel() {
            context.cancel(null);
            LOG.debug("Cancelled event streaming subscription: sessionId={}", sessionId);
        }
    }
}
