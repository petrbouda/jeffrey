/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.flamegraph.ai;

import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.shared.common.model.Type;

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
    ALLOCATION("classpath:flamegraph-ai/analysis-allocation.md"),
    NATIVE_MEMORY("classpath:flamegraph-ai/analysis-native-memory.md"),
    CPU("classpath:flamegraph-ai/analysis-cpu.md"),
    WALL_CLOCK("classpath:flamegraph-ai/analysis-wall-clock.md"),
    BLOCKING("classpath:flamegraph-ai/analysis-blocking.md"),
    GENERIC("classpath:flamegraph-ai/analysis-generic.md");

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
