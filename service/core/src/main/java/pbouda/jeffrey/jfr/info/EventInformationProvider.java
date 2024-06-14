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

package pbouda.jeffrey.jfr.info;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.jfr.event.AllEventsProvider;
import pbouda.jeffrey.jfr.event.EventSummary;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

public class EventInformationProvider implements Supplier<ArrayNode> {

    private final Path recording;
    private final CompositeExtraInfoEnhancer extraInfoEnhancer;
    private final List<Type> supportedEvents;

    public EventInformationProvider(Path recording) {
        this(recording, null);
    }

    public EventInformationProvider(Path recording, List<Type> supportedEvents) {
        this.recording = recording;
        this.extraInfoEnhancer = new CompositeExtraInfoEnhancer(recording);
        this.supportedEvents = supportedEvents;
        this.extraInfoEnhancer.initialize();
    }

    private static ObjectNode eventSummaryToJson(EventSummary event) {
        return Json.createObject()
                .put("label", event.eventType().getLabel())
                .put("code", event.eventType().getName())
                .put("samples", event.samples())
                .put("weight", event.weight());
    }

    @Override
    public ArrayNode get() {
        List<EventSummary> events = new AllEventsProvider(recording, supportedEvents).get();
        ArrayNode arrayNode = Json.createArray();
        for (EventSummary event : events) {
            ObjectNode object = eventSummaryToJson(event);
            extraInfoEnhancer.accept(event.eventType(), object);
            arrayNode.add(object);
        }
        return arrayNode;
    }
}
