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

package pbouda.jeffrey.provider.profile;

import pbouda.jeffrey.provider.profile.model.*;

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
    void onEvent(Event event);

    /**
     * This method is called when an event setting is received.
     * {@link EventSetting} can be received multiple times and duplicated.
     *
     * @param setting the event setting to be written
     */
    void onEventSetting(EventSetting setting);

    /**
     * This method is called when an event type is received.
     * {@link EventType} can be received multiple times and duplicated.
     *
     * @param eventType the event type to be written
     */
    void onEventType(EventType eventType);

    /**
     * This method is called when an event stacktrace is received.
     *
     * @param stacktrace the event stacktrace to be written
     * @return ID of the stacktrace
     */
    long onEventStacktrace(EventStacktrace stacktrace);

    /**
     * This method is called when an event thread is received.
     * {@link EventThread} can be received multiple times and duplicated.
     *
     * @param thread the event thread to be written
     * @return ID of the thread
     */
    long onEventThread(EventThread thread);

    /**
     * This method is called when the thread that pushes the data to persist finishes.
     * All threads that participates needs to call this method.
     */
    void onThreadComplete();
}
