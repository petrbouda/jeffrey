/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.flamegraph.export;

import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.microscope.model.Type;

import java.util.Set;

/**
 * Selects an analysis-instruction recipe to embed in a flamegraph AI export
 * based on the event type of the profile being exported.
 * <p>
 * Each constant loads its body eagerly from the classpath at class-init time
 * via {@link FileSystemUtils#readString(String)}. A missing resource raises
 * a {@link RuntimeException} out of {@code <clinit>}, which aborts JVM class
 * loading — the intended loud-startup-failure behaviour.
 */
enum AnalysisCategory {
    ALLOCATION("classpath:flamegraph-export/analysis-allocation.md"),
    NATIVE_MEMORY("classpath:flamegraph-export/analysis-native-memory.md"),
    CPU("classpath:flamegraph-export/analysis-cpu.md"),
    WALL_CLOCK("classpath:flamegraph-export/analysis-wall-clock.md"),
    BLOCKING("classpath:flamegraph-export/analysis-blocking.md"),
    GENERIC("classpath:flamegraph-export/analysis-generic.md");

    private static final Set<Type> NATIVE_MEMORY_TYPES = Set.of(Type.MALLOC, Type.NATIVE_LEAK);

    private final String instruction;

    AnalysisCategory(String resourcePath) {
        this.instruction = FileSystemUtils.readString(resourcePath);
    }

    String instruction() {
        return instruction;
    }

    static AnalysisCategory resolve(Type eventType) {
        if (eventType.isAllocationEvent()) {
            return ALLOCATION;
        }
        if (NATIVE_MEMORY_TYPES.contains(eventType)) {
            return NATIVE_MEMORY;
        }
        if (Type.EXECUTION_SAMPLE.equals(eventType)) {
            return CPU;
        }
        if (Type.WALL_CLOCK_SAMPLE.equals(eventType) || eventType.isMethodTraceEvent()) {
            return WALL_CLOCK;
        }
        if (eventType.isBlockingEvent()) {
            return BLOCKING;
        }
        return GENERIC;
    }
}
