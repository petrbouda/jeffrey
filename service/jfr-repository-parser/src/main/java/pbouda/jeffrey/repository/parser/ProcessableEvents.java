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

package pbouda.jeffrey.repository.parser;

import pbouda.jeffrey.shared.model.Type;

import java.util.Collection;
import java.util.List;

/**
 * Defines which events should be processed by an EventProcessor.
 * Can either process all events or filter to specific event types.
 */
public class ProcessableEvents {

    private final boolean processableAll;
    private final Collection<Type> events;

    private ProcessableEvents(boolean processableAll) {
        this(processableAll, List.of());
    }

    private ProcessableEvents(boolean processableAll, Collection<Type> events) {
        this.processableAll = processableAll;
        this.events = events;
    }

    /**
     * Creates an instance that processes all events.
     *
     * @return ProcessableEvents configured to process all events
     */
    public static ProcessableEvents all() {
        return new ProcessableEvents(true);
    }

    /**
     * Creates an instance that processes only the specified event type.
     *
     * @param event the event type to process
     * @return ProcessableEvents configured to process only the specified event
     */
    public static ProcessableEvents of(Type event) {
        return new ProcessableEvents(false, List.of(event));
    }

    /**
     * Creates an instance that processes only the specified event types.
     *
     * @param events the event types to process
     * @return ProcessableEvents configured to process only the specified events
     */
    public static ProcessableEvents of(Collection<Type> events) {
        return new ProcessableEvents(false, events);
    }

    /**
     * @return the collection of event types to process
     */
    public Collection<Type> events() {
        return events;
    }

    /**
     * @return true if all events should be processed
     */
    public boolean isProcessableAll() {
        return processableAll;
    }
}
