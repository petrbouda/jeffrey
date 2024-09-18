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

package pbouda.jeffrey.jfr.configuration;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.consumer.RecordedEvent;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.jfrparser.api.SingleEventProcessor;

import java.util.List;
import java.util.Optional;

public class JsonFieldEventProcessor extends SingleEventProcessor<Optional<JsonContent>> {

    private static final List<String> IGNORED_FIELDS = List.of("eventThread", "duration", "startTime", "stackTrace");

    private JsonContent content = null;

    public JsonFieldEventProcessor(Type eventType) {
        super(eventType);
    }

    @Override
    public Result onEvent(RecordedEvent event) {
        ObjectNode node = Json.createObject();
        for (ValueDescriptor field : event.getFields()) {
            if (!IGNORED_FIELDS.contains(field.getName())) {
                Object value = event.getValue(field.getName());
                node.put(field.getLabel(), safeToString(value));
            }
        }
        this.content = new JsonContent(event.getEventType().getLabel(), node);
        return Result.DONE;
    }

    private static String safeToString(Object val) {
        return val == null ? null : val.toString();
    }

    @Override
    public Optional<JsonContent> get() {
        return Optional.ofNullable(content);
    }
}
