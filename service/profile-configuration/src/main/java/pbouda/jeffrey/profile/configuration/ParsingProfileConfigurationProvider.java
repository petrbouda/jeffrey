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

package pbouda.jeffrey.profile.configuration;

import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.jfrparser.jdk.JdkRecordingIterators;

import java.nio.file.Path;
import java.util.List;

public class ParsingProfileConfigurationProvider implements ProfileConfigurationProvider {

    private static final List<Type> EVENT_TYPES = List.of(
            Type.JVM_INFORMATION,
            Type.CONTAINER_CONFIGURATION,
            Type.CPU_INFORMATION,
            Type.OS_INFORMATION,
            Type.GC_CONFIGURATION,
            Type.GC_HEAP_CONFIGURATION,
            Type.GC_SURVIVOR_CONFIGURATION,
            Type.GC_TLAB_CONFIGURATION,
            Type.YOUNG_GENERATION_CONFIGURATION,
            Type.COMPILER_CONFIGURATION,
            Type.VIRTUALIZATION_INFORMATION
    );

    private final List<Path> recordings;

    public ParsingProfileConfigurationProvider(List<Path> recordings) {
        this.recordings = recordings;
    }

    @Override
    public ObjectNode get() {
        ObjectNode result = Json.createObject();
        for (Type eventType : EVENT_TYPES) {
            JsonContent content = JdkRecordingIterators.automaticAndCollectPartial(
                    recordings, () -> new JsonFieldEventProcessor(eventType), new JsonFieldEventCollector());

            if (content != null) {
                result.set(content.name(), content.content());
            }
        }
        return result;
    }
}
