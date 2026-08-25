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

package cafe.jeffrey.shared.common.jfr;

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
