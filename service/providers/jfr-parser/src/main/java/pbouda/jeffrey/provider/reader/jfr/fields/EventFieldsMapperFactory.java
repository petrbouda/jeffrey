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

package pbouda.jeffrey.provider.reader.jfr.fields;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jdk.jfr.EventType;
import jdk.jfr.consumer.RecordedEvent;
import pbouda.jeffrey.common.model.EventFieldsSetting;

import java.util.List;

public class EventFieldsMapperFactory {

    private final EventFieldsSetting eventFieldsSetting;

    public EventFieldsMapperFactory(EventFieldsSetting eventFieldsSetting) {
        this.eventFieldsSetting = eventFieldsSetting;
    }

    public EventFieldsMapper create() {
        return switch (eventFieldsSetting) {
            case ALL -> new EventFieldsToJsonMapper();
            case NONE -> new NoOpEventFieldsMapper();
            case MANDATORY -> new MandatoryEventFieldsMapper();
        };
    }

    private static class NoOpEventFieldsMapper implements EventFieldsMapper {
        @Override
        public void update(List<EventType> eventTypes) {
        }

        @Override
        public ObjectNode map(RecordedEvent event) {
            return null;
        }
    }
}
