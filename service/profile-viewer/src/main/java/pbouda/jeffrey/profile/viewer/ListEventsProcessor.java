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

package pbouda.jeffrey.profile.viewer;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jdk.jfr.EventType;
import jdk.jfr.consumer.RecordedEvent;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.jfrparser.api.EventProcessor;
import pbouda.jeffrey.jfrparser.api.ProcessableEvents;

import java.util.List;

public class ListEventsProcessor implements EventProcessor<ArrayNode> {

    private final ArrayNode result = Json.createArray();
    private final ProcessableEvents processableEvents;
    private EventFieldsToJsonMapper eventFieldsToJsonMapper;

    public ListEventsProcessor(Type eventType) {
        this.processableEvents = new ProcessableEvents(eventType);
    }

    @Override
    public ProcessableEvents processableEvents() {
        return processableEvents;
    }

    @Override
    public void onStart(List<EventType> eventTypes) {
        this.eventFieldsToJsonMapper = new EventFieldsToJsonMapper(eventTypes);
    }

    @Override
    public Result onEvent(RecordedEvent event) {
        ObjectNode node = eventFieldsToJsonMapper.map(event);
        result.add(node);
        return Result.CONTINUE;
    }

    @Override
    public ArrayNode get() {
        return result;
    }
}
