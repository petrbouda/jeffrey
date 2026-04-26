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

package pbouda.jeffrey.local.core.client.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.local.core.client.grpc.RemoteEventStreamingClient.EventStreamingSubscription;
import pbouda.jeffrey.server.api.v1.EventBatch;

import java.util.Set;

/**
 * Shared {@link StreamObserver} for gRPC event batch streaming.
 * Handles batch delivery, error logging with cancellation detection, and cleanup on completion.
 */
class EventBatchStreamObserver implements StreamObserver<EventBatch> {

    private static final Logger LOG = LoggerFactory.getLogger(EventBatchStreamObserver.class);

    private final String sessionId;
    private final EventStreamingSubscription subscription;
    private final Set<EventStreamingSubscription> activeSubscriptions;
    private final StreamingCallbacks callbacks;

    EventBatchStreamObserver(
            String sessionId,
            EventStreamingSubscription subscription,
            Set<EventStreamingSubscription> activeSubscriptions,
            StreamingCallbacks callbacks) {

        this.sessionId = sessionId;
        this.subscription = subscription;
        this.activeSubscriptions = activeSubscriptions;
        this.callbacks = callbacks;
    }

    @Override
    public void onNext(EventBatch batch) {
        callbacks.onNext().accept(batch);
    }

    @Override
    public void onError(Throwable t) {
        activeSubscriptions.remove(subscription);
        if (Status.fromThrowable(t).getCode() == Status.Code.CANCELLED) {
            LOG.info("Streaming cancelled: sessionId={}", sessionId);
        } else {
            LOG.warn("Streaming error: sessionId={}", sessionId, t);
        }
        callbacks.onError().accept(t);
    }

    @Override
    public void onCompleted() {
        activeSubscriptions.remove(subscription);
        LOG.debug("Streaming completed: sessionId={}", sessionId);
        callbacks.onComplete().run();
    }
}
