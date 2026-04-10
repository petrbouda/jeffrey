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
import pbouda.jeffrey.shared.common.IDGenerator;

import java.io.Closeable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks active {@link ReplayStreamingSubscriber} instances and provides lifecycle
 * management: start, stop, and close all on shutdown.
 */
public class ReplayStreamingManager implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(ReplayStreamingManager.class);

    private final ConcurrentHashMap<String, ReplayStreamingSubscriber> readers = new ConcurrentHashMap<>();

    /**
     * Creates and starts a new replay stream reader for the given subscription.
     *
     * @param subscription replay subscription with session, files, and time window
     * @param callbacks    streaming lifecycle callbacks
     * @return replay ID for later cancellation
     */
    public String subscribe(ReplayStreamSubscription subscription, StreamingCallbacks callbacks) {
        String replayId = IDGenerator.generate();
        ReplayStreamingSubscriber reader = new ReplayStreamingSubscriber(
                subscription, callbacks.withOnClose(() -> removeReader(replayId)));

        readers.put(replayId, reader);
        reader.start();

        LOG.info("Started replay stream: subscription={} replayId={}", subscription, replayId);
        return replayId;
    }

    /**
     * Stops a specific replay stream.
     */
    public void unsubscribe(String subscriptionId) {
        ReplayStreamingSubscriber reader = readers.get(subscriptionId);
        if (reader != null) {
            reader.close();
        }
        LOG.info("Stopped replay stream: subscriptionId={}", subscriptionId);
    }

    @Override
    public void close() {
        LOG.info("Closing all replay streams: count={}", readers.size());
        readers.forEach((_, reader) -> reader.close());
        readers.clear();
    }

    private void removeReader(String replayId) {
        ReplayStreamingSubscriber reader = readers.remove(replayId);
        if (reader != null) {
            LOG.debug("Removed replay stream reader: replayId={}", replayId);
        }
    }
}
