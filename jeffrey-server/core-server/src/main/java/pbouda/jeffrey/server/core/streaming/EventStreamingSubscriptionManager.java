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
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tracks active per-subscriber {@link SubscriberEventStream} instances per session.
 * Provides lifecycle management: subscribe, unsubscribe, close all for a session,
 * and close all on server shutdown.
 */
public class EventStreamingSubscriptionManager implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(EventStreamingSubscriptionManager.class);

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SubscriberEventStream>> subscriptions =
            new ConcurrentHashMap<>();

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
                sessionId, sessionPath, eventTypes, startTime, endTime, sendEmptyBatches, observer);

        subscriptions.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(stream);
        stream.start();

        LOG.info("Subscribed to event stream: sessionId={} eventTypes={}", sessionId, eventTypes);
        return stream;
    }

    /**
     * Removes and closes a specific subscriber event stream.
     */
    public void unsubscribe(String sessionId, SubscriberEventStream stream) {
        CopyOnWriteArrayList<SubscriberEventStream> streams = subscriptions.get(sessionId);
        if (streams != null) {
            streams.remove(stream);
            if (streams.isEmpty()) {
                subscriptions.remove(sessionId, streams);
            }
        }
        stream.close();
        LOG.debug("Unsubscribed from event stream: sessionId={}", sessionId);
    }

    /**
     * Closes all subscriber event streams for a given session.
     * Called when a session finishes.
     */
    public void closeAllForSession(String sessionId) {
        CopyOnWriteArrayList<SubscriberEventStream> streams = subscriptions.remove(sessionId);
        if (streams != null && !streams.isEmpty()) {
            LOG.info("Closing all subscriber streams for session: sessionId={} count={}", sessionId, streams.size());
            streams.forEach(SubscriberEventStream::close);
        }
    }

    @Override
    public void close() {
        LOG.info("Closing all subscriber event streams: sessionCount={}", subscriptions.size());
        for (List<SubscriberEventStream> streams : subscriptions.values()) {
            streams.forEach(SubscriberEventStream::close);
        }
        subscriptions.clear();
    }
}
