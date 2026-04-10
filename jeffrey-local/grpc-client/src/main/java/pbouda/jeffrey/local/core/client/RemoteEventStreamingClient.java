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

package pbouda.jeffrey.local.core.client;

import io.grpc.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.server.api.v1.EventStreamingServiceGrpc;
import pbouda.jeffrey.server.api.v1.LiveStreamingRequest;
import pbouda.jeffrey.server.api.v1.ReplayStreamingRequest;

import java.io.Closeable;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * gRPC client for live streaming and replaying JFR events from a remote Jeffrey server.
 * Uses an async stub for long-lived server-streaming subscriptions.
 */
public class RemoteEventStreamingClient implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteEventStreamingClient.class);

    private final EventStreamingServiceGrpc.EventStreamingServiceStub stub;
    private final Set<EventStreamingSubscription> activeSubscriptions = ConcurrentHashMap.newKeySet();

    public RemoteEventStreamingClient(GrpcServerConnection connection) {
        this.stub = EventStreamingServiceGrpc.newStub(connection.getChannel());
    }

    /**
     * Subscribes to live JFR events from a single session on the remote server.
     * Always continuous — the stream stays open waiting for new events.
     *
     * @param sessionId the session ID to subscribe to
     * @param request   subscription parameters (event types)
     * @param callbacks streaming lifecycle callbacks (onBatch, onComplete, onError)
     * @return a cancellation handle to stop the subscription
     */
    public EventStreamingSubscription subscribeLiveStreaming(
            String sessionId,
            LiveSubscriptionRequest request,
            StreamingCallbacks callbacks) {

        LiveStreamingRequest liveRequest = LiveStreamingRequest.newBuilder()
                .setSessionId(sessionId)
                .addAllEventTypes(request.eventTypes())
                .setSendEmptyBatches(true)
                .build();

        Context.CancellableContext cancellableContext = Context.current().withCancellation();
        EventStreamingSubscription subscription = new EventStreamingSubscription(cancellableContext, sessionId);
        activeSubscriptions.add(subscription);

        cancellableContext.run(() -> stub.liveStreaming(liveRequest,
                new EventBatchStreamObserver(sessionId, subscription, activeSubscriptions, callbacks)));

        LOG.info("Subscribed to live stream: sessionId={}", sessionId);
        return subscription;
    }

    /**
     * Replays historical JFR events from dumped recording files on the remote server.
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

        String sessionId = request.sessionId();
        Context.CancellableContext cancellableContext = Context.current().withCancellation();
        EventStreamingSubscription subscription = new EventStreamingSubscription(cancellableContext, sessionId);
        activeSubscriptions.add(subscription);

        cancellableContext.run(() -> stub.replayStreaming(requestBuilder.build(),
                new EventBatchStreamObserver(sessionId, subscription, activeSubscriptions, callbacks)));

        LOG.info("Subscribed to replay stream: request={}", request);
        return subscription;
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
