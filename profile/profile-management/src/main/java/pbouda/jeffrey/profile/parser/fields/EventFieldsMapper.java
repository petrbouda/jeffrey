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

package pbouda.jeffrey.profile.parser.fields;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jdk.jfr.EventType;
import jdk.jfr.consumer.RecordedEvent;

import java.util.List;

public interface EventFieldsMapper {

    /**
     * Update event-types of the internal implementation
     *
     * @param eventTypes a list of EventType to update
     */
    void update(List<EventType> eventTypes);

    /**
     * Maps the fields of the {@link RecordedEvent} to the JSON object.
     * TODO: change to byte[] to support binary encoding
     *
     * @param event the event to be mapped
     * @return the JSON object with the fields of the event
     */
    ObjectNode map(RecordedEvent event);
}
