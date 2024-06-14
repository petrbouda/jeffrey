/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.jfrparser.jdk;

import jdk.jfr.consumer.RecordedEvent;

public interface EventProcessor {

    enum Result {
        CONTINUE, DONE
    }

    /**
     * A collection contains all name of events that can be processed by the implementation of this interface.
     * Other events will be skipped and method {@link #onEvent(RecordedEvent)} won't be invoked.
     *
     * @return all events eligible for processing.
     */
    ProcessableEvents processableEvents();

    /**
     * This method is called before any event is passed to the processor.
     */
    default void onStart() {
    }

    /**
     * Processes incoming event. This method is invoked after calling {@link #onStart()}, before {@link #onComplete()}
     * and can be called multiple-times.
     * Methods returns the result of the processing, if we want to continue, or the processing has been finished.
     * If {@link Result#DONE} is returned, then the {@link #onComplete()} is supposed to be called anyway.
     *
     * @param event event to process.
     * @return result of the processing.
     */
    Result onEvent(RecordedEvent event);

    /**
     * Finalizes the event processing. No more events will deliver after calling this method.
     */
    default void onComplete() {
    }
}
