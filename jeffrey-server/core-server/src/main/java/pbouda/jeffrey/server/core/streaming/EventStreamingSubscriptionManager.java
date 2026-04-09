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

package pbouda.jeffrey.server.core.streaming;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.server.api.v1.EventBatch;
import pbouda.jeffrey.shared.common.IDGenerator;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Tracks active {@link EventStreamSubscriber} instances and provides lifecycle
 * management: subscribe, unsubscribe, close all for a session, and close on shutdown.
 */
public class EventStreamingSubscriptionManager implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(EventStreamingSubscriptionManager.class);

    private final ConcurrentHashMap<String, EventStreamSubscriber> subscriptions = new ConcurrentHashMap<>();

    /**
     * Creates and starts a new subscriber event stream for the given session.
     *
     * @param subscription subscription with all data to start streaming from repo.
     * @return subscription ID
     * @throws IOException if the streaming repository cannot be opened
     */
    public String subscribe(
            EventStreamSubscription subscription,
            Consumer<EventBatch> consumer,
            Runnable onComplete,
            Consumer<Throwable> onError) throws IOException {

        String subscriptionId = IDGenerator.generate();
        EventStreamSubscriber stream = new EventStreamSubscriber(
                subscription, consumer, onComplete, onError, () -> removeSubscriber(subscriptionId));

        subscriptions.put(subscriptionId, stream);
        stream.start();

        LOG.info("Subscribed to event stream: subscription={} subscription_id={}", subscription, subscriptionId);
        return subscriptionId;
    }

    /**
     * Closes a specific subscriber stream. The stream's completion callback removes
     * the entry from the subscription set.
     */
    public void unsubscribe(String subscriptionId) {
        subscriptions.get(subscriptionId).close();
        LOG.info("Unsubscribed from event stream: subscription_id={}", subscriptionId);
    }

    @Override
    public void close() {
        LOG.info("Closing all subscriber event streams: count={}", subscriptions.size());
        subscriptions.forEach((_, stream) -> stream.close());
        subscriptions.clear();
    }

    private void removeSubscriber(String subscriptionId) {
        EventStreamSubscriber stream = subscriptions.remove(subscriptionId);
        if (stream != null) {
            LOG.debug("Removed subscriber from subscriber list: subscription={}", subscriptionId);
        }
    }
}
