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

package cafe.jeffrey.provider.profile.api;

/**
 * Sink for the events parsed out of a single JFR recording (or chunk).
 *
 * <h2>Threading</h2>
 * A given instance is driven by exactly <strong>one</strong> thread: every method is invoked
 * sequentially, never concurrently, so implementations need no synchronization or thread-safe
 * collections. Parallel parsing uses a separate instance per chunk (see
 * {@link EventWriter#newSingleThreadedWriter()}); combining those partial results is the
 * {@link EventWriter}'s responsibility, not this writer's.
 *
 * <h2>Call protocol</h2>
 * <ol>
 *   <li>{@link #onThreadStart()} once, before any event.</li>
 *   <li>For each parsed event, its thread and stacktrace are <em>deduplicated</em>:
 *       {@link #onEventThread(EventThread)} and {@link #onEventStacktrace(EventStacktrace)} are called
 *       only the <em>first</em> time a distinct thread/stacktrace is seen, immediately before the
 *       {@link #onEvent(Event)} that introduced it. Each returns an id, which is then stamped onto
 *       {@link Event#threadId()} / {@link Event#stacktraceId()} of that event and of every later event
 *       that reuses the same thread/stacktrace. Reuses arrive as {@link #onEvent(Event)} only — there is
 *       <em>no</em> re-announce. An implementation that needs the thread/stacktrace at {@code onEvent}
 *       time must therefore retain it, keyed by the id it handed back.</li>
 *   <li>{@link #onEventSetting(EventSetting)} and {@link #onEventType(EventType)} may arrive at any
 *       point and may be delivered more than once.</li>
 *   <li>{@link #onThreadComplete()} once, after the last event.</li>
 * </ol>
 */
public interface SingleThreadedEventWriter {

    /**
     * This method needs to be called for every thread that participates in pushing data to writer.
     */
    default void onThreadStart() {
    }

    /**
     * This method is called when an event is received.
     *
     * @param event the event to be written
     */
    default void onEvent(Event event) {
    }

    /**
     * This method is called when an event setting is received.
     * {@link EventSetting} can be received multiple times and duplicated.
     *
     * @param setting the event setting to be written
     */
    default void onEventSetting(EventSetting setting) {
    }

    /**
     * This method is called when an event type is received.
     * {@link EventType} can be received multiple times and duplicated.
     *
     * @param eventType the event type to be written
     */
    default void onEventType(EventType eventType) {
    }

    /**
     * Called once per <em>distinct</em> stacktrace, immediately before the {@link #onEvent(Event)} that
     * first uses it. The returned id is reused via {@link Event#stacktraceId()} by every later event with
     * the same stacktrace (those arrive as {@code onEvent} only). Implementations that need the frames at
     * {@code onEvent} time must retain this stacktrace keyed by the returned id.
     *
     * @param stacktrace the event stacktrace to be written
     * @return ID of the stacktrace, later referenced by {@link Event#stacktraceId()}
     */
    default long onEventStacktrace(EventStacktrace stacktrace) {
        return -1;
    }

    /**
     * Called once per <em>distinct</em> thread, immediately before the {@link #onEvent(Event)} that first
     * uses it. The returned id is reused via {@link Event#threadId()} by every later event on the same
     * thread (those arrive as {@code onEvent} only).
     *
     * @param thread the event thread to be written
     * @return ID of the thread, later referenced by {@link Event#threadId()}
     */
    default long onEventThread(EventThread thread) {
        return -1;
    }

    /**
     * This method is called when the thread that pushes the data to persist finishes.
     * All threads that participates needs to call this method.
     */
    default void onThreadComplete() {
    }
}
