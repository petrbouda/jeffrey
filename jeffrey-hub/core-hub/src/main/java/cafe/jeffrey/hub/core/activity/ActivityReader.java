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

package cafe.jeffrey.hub.core.activity;

import cafe.jeffrey.hub.core.streaming.ReplayStreamSubscription;
import cafe.jeffrey.hub.core.streaming.ReplayStreamingSubscriber;
import cafe.jeffrey.hub.core.streaming.StreamingCallbacks;

import java.time.Instant;
import java.util.function.BiConsumer;

/**
 * The reader one scan drives, as much of it as the scan needs: start it, and close it to stop.
 *
 * <p>A seam rather than a direct call to {@link ReplayStreamingSubscriber}, because the two paths
 * that are hardest to get right — the two-reader bound and abandoning a scan that has exhausted its
 * event-type budget — are only reachable while a reader is mid-file, which no real recording can be
 * held at on demand.</p>
 */
interface ActivityReader {

    void start();

    void close();

    /** Opens a reader over one subscription. */
    @FunctionalInterface
    interface Factory {

        ActivityReader open(
                ReplayStreamSubscription subscription,
                StreamingCallbacks callbacks,
                BiConsumer<String, Instant> events);

        static Factory replay() {
            return (subscription, callbacks, events) -> {
                var subscriber = new ReplayStreamingSubscriber(subscription, callbacks, events);
                return new ActivityReader() {
                    @Override
                    public void start() {
                        subscriber.start();
                    }

                    @Override
                    public void close() {
                        subscriber.close();
                    }
                };
            };
        }
    }
}
