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

package cafe.jeffrey.hub.core.workspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.shared.common.IDGenerator;

import java.io.Closeable;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Tracks live workspace event subscriptions and provides their lifecycle: subscribe,
 * unsubscribe, and close-all on shutdown. Mirrors the JFR streaming managers.
 *
 * <p>Subscribers are registered against {@link WorkspaceEventNotifier}, which delivers a
 * payload-free wakeup per append. Each subscriber then re-reads the queue by offset, so this
 * class holds no event buffer of its own — the durable queue is the buffer, which is what
 * stops a slow client from growing hub memory without bound.
 */
public class WorkspaceEventStreamingManager implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceEventStreamingManager.class);

    /**
     * A leaking client must not be able to spawn unbounded threads and database load, so
     * subscriptions are capped and the caller is expected to surface the rejection.
     */
    public static final int MAX_SUBSCRIPTIONS = 64;

    private final ConcurrentHashMap<String, WorkspaceEventSubscriber> subscribers = new ConcurrentHashMap<>();

    private final WorkspaceEventReader reader;
    private final Executor executor;
    private final Duration backstopInterval;

    public WorkspaceEventStreamingManager(
            WorkspaceEventReader reader,
            WorkspaceEventNotifier notifier,
            Executor executor,
            Duration backstopInterval) {

        this.reader = reader;
        this.executor = executor;
        this.backstopInterval = backstopInterval;
        notifier.addListener(this::onWorkspaceEventsAppended);
    }

    /**
     * Starts a subscription and returns its id.
     *
     * @throws TooManySubscriptionsException when the subscription cap is reached
     */
    public String subscribe(WorkspaceEventSubscription subscription, WorkspaceEventCallbacks callbacks) {
        if (subscribers.size() >= MAX_SUBSCRIPTIONS) {
            throw new TooManySubscriptionsException(
                    "Too many active workspace event subscriptions: max=" + MAX_SUBSCRIPTIONS);
        }

        String subscriptionId = IDGenerator.generate();
        WorkspaceEventSubscriber subscriber = new WorkspaceEventSubscriber(
                subscription,
                callbacks.withOnClose(() -> subscribers.remove(subscriptionId)),
                reader,
                backstopInterval);

        subscribers.put(subscriptionId, subscriber);
        executor.execute(subscriber::run);

        LOG.info("Subscribed to workspace events: subscription={} subscription_id={}", subscription, subscriptionId);
        return subscriptionId;
    }

    public void unsubscribe(String subscriptionId) {
        WorkspaceEventSubscriber subscriber = subscribers.get(subscriptionId);
        if (subscriber != null) {
            subscriber.close();
        }
        LOG.info("Unsubscribed from workspace events: subscription_id={}", subscriptionId);
    }

    /**
     * Wakes every subscriber watching the given workspace. Registered with the notifier.
     */
    void onWorkspaceEventsAppended(String workspaceId) {
        for (WorkspaceEventSubscriber subscriber : subscribers.values()) {
            if (subscriber.workspaceId().equals(workspaceId)) {
                subscriber.wakeUp();
            }
        }
    }

    public int activeSubscriptions() {
        return subscribers.size();
    }

    @Override
    public void close() {
        LOG.info("Closing all workspace event subscriptions: count={}", subscribers.size());
        subscribers.forEach((_, subscriber) -> subscriber.close());
        subscribers.clear();
    }

    /**
     * Thrown when the subscription cap is reached. Deliberately a plain domain exception —
     * the gRPC boundary maps it to {@code RESOURCE_EXHAUSTED}.
     */
    public static class TooManySubscriptionsException extends RuntimeException {

        public TooManySubscriptionsException(String message) {
            super(message);
        }
    }
}
