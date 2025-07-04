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

package pbouda.jeffrey.provider.reader.jfr;

import pbouda.jeffrey.common.model.Type;

import java.util.HashMap;
import java.util.Map;

import static pbouda.jeffrey.common.model.Type.*;

public class WeightExtractorRegistry {

    private static Map<Type, WeightExtractor> REGISTRY;

    static {
        REGISTRY = new HashMap<>();
        REGISTRY.put(NATIVE_LEAK, WeightExtractor.allocation("size"));
        REGISTRY.put(MALLOC, WeightExtractor.allocation("size", e -> String.valueOf(e.getLong("address"))));
        REGISTRY.put(FREE, WeightExtractor.allocationEntityOnly(e -> String.valueOf(e.getLong("address"))));
        REGISTRY.put(JAVA_MONITOR_ENTER, WeightExtractor.duration("monitorClass"));
        REGISTRY.put(JAVA_MONITOR_WAIT, WeightExtractor.duration("monitorClass"));
        REGISTRY.put(THREAD_PARK, WeightExtractor.duration("parkedClass"));
        REGISTRY.put(THREAD_SLEEP, WeightExtractor.duration());
        REGISTRY.put(OBJECT_ALLOCATION_IN_NEW_TLAB, WeightExtractor.allocation("allocationSize", "objectClass"));
        REGISTRY.put(OBJECT_ALLOCATION_OUTSIDE_TLAB, WeightExtractor.allocation("allocationSize", "objectClass"));
        REGISTRY.put(OBJECT_ALLOCATION_SAMPLE, WeightExtractor.allocation("weight", "objectClass"));
        REGISTRY.put(SOCKET_READ, WeightExtractor.allocation("bytesRead"));
        REGISTRY.put(SOCKET_WRITE, WeightExtractor.allocation("bytesWritten"));
        REGISTRY.put(FILE_READ, WeightExtractor.allocation("bytesRead"));
        REGISTRY.put(FILE_WRITE, WeightExtractor.allocation("bytesWritten"));
        REGISTRY.put(THREAD_ALLOCATION_STATISTICS, WeightExtractor.allocation("allocated"));
    }

    public static WeightExtractor resolve(Type type) {
        return REGISTRY.get(type);
    }
}
