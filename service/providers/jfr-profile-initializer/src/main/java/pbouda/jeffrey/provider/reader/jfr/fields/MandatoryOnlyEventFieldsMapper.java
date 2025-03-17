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
import pbouda.jeffrey.common.model.Type;

import java.util.List;

public class MandatoryOnlyEventFieldsMapper implements EventFieldsMapper {

    private static final List<String> ALLOWED_EVENT_TYPES = List.of(
            // For Configuration Section
            Type.JVM_INFORMATION.code(),
            Type.CONTAINER_CONFIGURATION.code(),
            Type.CPU_INFORMATION.code(),
            Type.OS_INFORMATION.code(),
            Type.GC_CONFIGURATION.code(),
            Type.GC_HEAP_CONFIGURATION.code(),
            Type.GC_SURVIVOR_CONFIGURATION.code(),
            Type.GC_TLAB_CONFIGURATION.code(),
            Type.YOUNG_GENERATION_CONFIGURATION.code(),
            Type.COMPILER_CONFIGURATION.code(),
            Type.VIRTUALIZATION_INFORMATION.code(),

            // For Threads Section
            Type.THREAD_START.code(),
            Type.THREAD_END.code(),
            Type.THREAD_PARK.code(),
            Type.THREAD_SLEEP.code(),
            Type.JAVA_MONITOR_ENTER.code(),
            Type.JAVA_MONITOR_WAIT.code(),
            Type.SOCKET_READ.code(),
            Type.SOCKET_WRITE.code(),
            Type.FILE_READ.code(),
            Type.FILE_WRITE.code()
    );

    private final EventFieldsMapper delegatedMapper;

    public MandatoryOnlyEventFieldsMapper(List<EventType> eventTypes) {
        this.delegatedMapper = new EventFieldsToJsonMapper(eventTypes);
    }

    @Override
    public ObjectNode map(RecordedEvent event) {
        String eventName = event.getEventType().getName();
        return ALLOWED_EVENT_TYPES.contains(eventName) ? delegatedMapper.map(event) : null;
    }
}
