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

import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.server.api.v1.EventBatch;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks active {@link SubscriberEventStream} instances and provides lifecycle
 * management: subscribe, unsubscribe, close all for a session, and close on shutdown.
 *
 * <p>Subscribers register themselves into a flat concurrent set. Each stream is wired
 * with a completion callback that removes itself from the set on any terminal event
 * (natural end, error, or explicit close), so the set is always symmetric across
 * lifecycle exits.</p>
 */
public class EventStreamingSubscriptionManager implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(EventStreamingSubscriptionManager.class);

    private final Set<SubscriberEventStream> subscriptions = ConcurrentHashMap.newKeySet();
    private final Clock clock;

    public EventStreamingSubscriptionManager(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Creates and starts a new subscriber event stream for the given session.
     *
     * @param sessionId         the session ID
     * @param sessionPath       the filesystem path to the session directory
     * @param eventTypes        JFR event types to subscribe to (empty = all)
     * @param startTime         optional start time for historical replay
     * @param endTime           optional end time to stop streaming
     * @param sendEmptyBatches  whether to send empty batches as heartbeats
     * @param observer          the gRPC stream observer to send batches to
     * @return the created subscriber event stream
     * @throws IOException if the streaming repository cannot be opened
     */
    public SubscriberEventStream subscribe(
            String sessionId,
            Path sessionPath,
            Set<String> eventTypes,
            Instant startTime,
            Instant endTime,
            boolean sendEmptyBatches,
            StreamObserver<EventBatch> observer) throws IOException {

        SubscriberEventStream stream = new SubscriberEventStream(
                sessionId, sessionPath, eventTypes, startTime, endTime,
                sendEmptyBatches, observer, clock, this::removeSubscription);

        subscriptions.add(stream);
        stream.start();

        LOG.info("Subscribed to event stream: sessionId={} eventTypes={}", sessionId, eventTypes);
        return stream;
    }

    /**
     * Closes a specific subscriber stream. The stream's completion callback removes
     * the entry from the subscription set.
     */
    public void unsubscribe(SubscriberEventStream stream) {
        stream.close();
        LOG.debug("Unsubscribed from event stream: sessionId={}", stream.sessionId());
    }

    /**
     * Closes all subscriber streams belonging to the given session. Called when a
     * session finishes.
     */
    public void closeAllForSession(String sessionId) {
        List<SubscriberEventStream> matching = subscriptions.stream()
                .filter(s -> s.sessionId().equals(sessionId))
                .toList();
        if (!matching.isEmpty()) {
            LOG.info("Closing all subscriber streams for session: sessionId={} count={}", sessionId, matching.size());
            matching.forEach(SubscriberEventStream::close);
        }
    }

    @Override
    public void close() {
        LOG.info("Closing all subscriber event streams: count={}", subscriptions.size());
        List.copyOf(subscriptions).forEach(SubscriberEventStream::close);
        subscriptions.clear();
    }

    private void removeSubscription(SubscriberEventStream stream) {
        if (subscriptions.remove(stream)) {
            LOG.debug("Removed completed subscriber stream: sessionId={}", stream.sessionId());
        }
    }
}
