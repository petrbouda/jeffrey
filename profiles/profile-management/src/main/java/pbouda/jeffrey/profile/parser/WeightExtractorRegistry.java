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

package pbouda.jeffrey.profile.parser;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedStackTrace;
import pbouda.jeffrey.shared.common.model.Type;

import java.util.Collections;
import java.util.HashMap;

import static pbouda.jeffrey.shared.common.model.Type.*;
import java.util.List;
import java.util.Map;

public class WeightExtractorRegistry {

    private static final Map<Type, WeightExtractor> REGISTRY = Collections.unmodifiableMap(buildRegistry());

    private static Map<Type, WeightExtractor> buildRegistry() {
        Map<Type, WeightExtractor> registry = new HashMap<>();
        registry.put(NATIVE_LEAK, WeightExtractor.allocation("size"));
        registry.put(METHOD_TRACE, WeightExtractor.duration(WeightExtractorRegistry::extractFirstFrame));
        registry.put(MALLOC, WeightExtractor.allocation("size", e -> String.valueOf(e.getLong("address"))));
        registry.put(FREE, WeightExtractor.allocationEntityOnly(e -> String.valueOf(e.getLong("address"))));
        registry.put(JAVA_MONITOR_ENTER, WeightExtractor.duration("monitorClass"));
        registry.put(JAVA_MONITOR_WAIT, WeightExtractor.duration("monitorClass"));
        registry.put(THREAD_PARK, WeightExtractor.duration("parkedClass"));
        registry.put(THREAD_SLEEP, WeightExtractor.duration());
        registry.put(OBJECT_ALLOCATION_IN_NEW_TLAB, WeightExtractor.allocation("tlabSize", "objectClass"));
        registry.put(OBJECT_ALLOCATION_OUTSIDE_TLAB, WeightExtractor.allocation("allocationSize", "objectClass"));
        registry.put(OBJECT_ALLOCATION_SAMPLE, WeightExtractor.allocation("weight", "objectClass"));
        registry.put(SOCKET_READ, WeightExtractor.allocation("bytesRead"));
        registry.put(SOCKET_WRITE, WeightExtractor.allocation("bytesWritten"));
        registry.put(FILE_READ, WeightExtractor.allocation("bytesRead"));
        registry.put(FILE_WRITE, WeightExtractor.allocation("bytesWritten"));
        registry.put(THREAD_ALLOCATION_STATISTICS, WeightExtractor.allocation("allocated"));
        return registry;
    }

    public static WeightExtractor resolve(Type type) {
        return REGISTRY.get(type);
    }

    private static String extractFirstFrame(RecordedEvent event) {
        RecordedStackTrace stackTrace = event.getStackTrace();
        if (stackTrace == null || stackTrace.getFrames().isEmpty()) {
            return null;
        }
        List<RecordedFrame> frames = stackTrace.getFrames();
        if (frames.isEmpty()) {
            return null;
        }
        RecordedFrame frame = frames.getFirst();
        return frame.getMethod().getType().getName() + "#" + frame.getMethod().getName();
    }
}
