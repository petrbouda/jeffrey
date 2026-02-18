/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.platform.streaming;

import jdk.jfr.consumer.RecordedEvent;

/**
 * Handler for a specific JFR event type consumed from a streaming repository.
 * Implementations are registered with {@link JfrStreamingConsumer} and invoked
 * when matching events arrive.
 */
public interface JfrStreamingHandler {

    /**
     * Returns the JFR event type name this handler is interested in
     * (e.g. {@code "jeffrey.Heartbeat"}).
     */
    String eventType();

    /**
     * Processes a single recorded event.
     */
    void onEvent(RecordedEvent event);

    /**
     * Lifecycle hook called after the event stream is opened but before the stream thread starts.
     * Handlers can override this to perform initialization that requires the stream closer
     * (e.g. starting a watchdog thread).
     *
     * @param streamCloser runnable that closes the JFR event stream (triggers natural close path)
     */
    default void initialize(Runnable streamCloser) {
    }

    /**
     * Optional cleanup hook called when the consumer shuts down.
     */
    default void close() {
    }
}
