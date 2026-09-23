/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.jfrparser.jdk;

import jdk.jfr.EventType;
import jdk.jfr.consumer.RecordedEvent;

import java.util.List;
import java.util.function.Supplier;

public interface EventProcessor<T> extends Supplier<T> {

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

    default void onMetadata(List<EventType> eventTypes) {
    }

    /**
     * Processes incoming event. This method is invoked after calling {@link #onStart(List)},
     * before {@link #onComplete()} and can be called multiple-times.
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
