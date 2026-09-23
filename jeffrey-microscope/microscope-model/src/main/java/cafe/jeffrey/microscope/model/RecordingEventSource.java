/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.microscope.model;

public enum RecordingEventSource {
    ASYNC_PROFILER(0, "Async-Profiler"),
    JDK(1, "JDK"),
    UNKNOWN(2, "Unknown"),
    HEAP_DUMP(3, "Heap Dump"),
    PPROF(4, "pprof"),
    OPEN_TELEMETRY(5, "OpenTelemetry");

    private final int id;
    private final String label;

    private static final RecordingEventSource[] VALUES = values();

    RecordingEventSource(int id, String label) {
        this.id = id;
        this.label = label;
    }

    public int getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public static RecordingEventSource byId(int id) {
        for (RecordingEventSource source : VALUES) {
            if (source.id == id) {
                return source;
            }
        }
        return null;
    }

    /**
     * Whether this source is an imported stack-sample profile format that Jeffrey only visualizes as
     * flamegraphs ({@link #PPROF}, {@link #OPEN_TELEMETRY}) — as opposed to a JFR-based recording
     * ({@link #JDK}, {@link #ASYNC_PROFILER}) that also feeds the JFR-specific analyses (event viewer,
     * thread viewer, GC, …). Used to skip those analyses for profiles that cannot support them.
     */
    public boolean isFlamegraphOnlyImport() {
        return this == PPROF || this == OPEN_TELEMETRY;
    }
}
