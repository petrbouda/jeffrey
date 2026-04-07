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
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.server.api.v1.EventBatch;
import pbouda.jeffrey.server.api.v1.EventStreamingServiceGrpc;
import pbouda.jeffrey.server.api.v1.SubscribeEventsRequest;

import java.io.Closeable;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * gRPC client for subscribing to live JFR events from a remote Jeffrey server session.
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
     * Subscribes to live JFR events from a session on the remote server.
     *
     * @param sessionId       the session ID
     * @param eventTypes      JFR event types to receive (empty = all events)
     * @param startTimeMillis optional start time in epoch millis (null for live-only)
     * @param endTimeMillis   optional end time in epoch millis (null for no limit)
     * @param continuous      when true, stream stays open waiting for new events
     * @param onBatch         callback for each received event batch
     * @param onComplete      callback when the stream ends (session finished or server closes)
     * @param onError         callback for stream errors
     * @return a cancellation handle to stop the subscription
     */
    public EventStreamingSubscription subscribe(
            String sessionId,
            Set<String> eventTypes,
            Long startTimeMillis,
            Long endTimeMillis,
            boolean continuous,
            Consumer<EventBatch> onBatch,
            Runnable onComplete,
            Consumer<Throwable> onError) {

        SubscribeEventsRequest.Builder requestBuilder = SubscribeEventsRequest.newBuilder()
                .setSessionId(sessionId)
                .addAllEventTypes(eventTypes)
                .setSendEmptyBatches(true)
                .setContinuous(continuous);

        if (startTimeMillis != null) {
            requestBuilder.setStartTime(startTimeMillis);
        }
        if (endTimeMillis != null) {
            requestBuilder.setEndTime(endTimeMillis);
        }

        Context.CancellableContext cancellableContext = Context.current().withCancellation();
        EventStreamingSubscription subscription = new EventStreamingSubscription(cancellableContext, sessionId);
        activeSubscriptions.add(subscription);

        cancellableContext.run(() ->
                stub.subscribeEvents(requestBuilder.build(), new StreamObserver<>() {
                    @Override
                    public void onNext(EventBatch batch) {
                        onBatch.accept(batch);
                    }

                    @Override
                    public void onError(Throwable t) {
                        activeSubscriptions.remove(subscription);
                        if (Status.fromThrowable(t).getCode() == Status.Code.CANCELLED) {
                            LOG.info("Event streaming cancelled: sessionId={}", sessionId);
                        } else {
                            LOG.warn("Event streaming error: sessionId={}", sessionId, t);
                        }
                        onError.accept(t);
                    }

                    @Override
                    public void onCompleted() {
                        activeSubscriptions.remove(subscription);
                        LOG.debug("Event streaming completed: sessionId={}", sessionId);
                        onComplete.run();
                    }
                }));

        LOG.info("Subscribed to event stream: sessionId={} eventTypes={}", sessionId, eventTypes);

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
