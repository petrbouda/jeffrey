/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.microscope.model.jfr;

import jdk.jfr.EventType;
import jdk.jfr.consumer.RecordedEvent;

import java.util.List;

/**
 * Turns a recorded event's fields into the JSON stored against it.
 * <p>
 * This runs once per event in the recording, which is what shapes the contract: it produces JSON
 * <em>text</em> rather than a tree, because the only thing downstream ever did with a tree was
 * serialize it. It also picks the one value worth pooling on the way past, because deciding that
 * needs the same values and would otherwise mean walking everything a second time.
 * <p>
 * Implementations are stateful and not thread-safe — one per parsing thread.
 */
public interface EventFieldsMapper {

    /**
     * Update event-types of the internal implementation
     *
     * @param eventTypes a list of EventType to update
     */
    void update(List<EventType> eventTypes);

    /**
     * Maps the fields of the {@link RecordedEvent} to JSON.
     *
     * @param event the event to be mapped
     * @return the event's fields as JSON, plus whichever value was lifted out for pooling
     */
    MappedFields map(RecordedEvent event);
}
