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
    ASYNC_PROFILER(0, "Async-Profiler", ProfileKind.JFR),
    JDK(1, "JDK", ProfileKind.JFR),
    // A JFR recording whose metadata could not be read yet; it is still parsed as JFR.
    UNKNOWN(2, "Unknown", ProfileKind.JFR),
    HEAP_DUMP(3, "Heap Dump", ProfileKind.HEAP_DUMP),
    PPROF(4, "pprof", ProfileKind.STACK_SAMPLES),
    OPEN_TELEMETRY(5, "OpenTelemetry", ProfileKind.STACK_SAMPLES),
    PERF_COUNTERS(6, "Perf Counters", ProfileKind.PERF_COUNTERS);

    private final int id;
    private final String label;
    private final ProfileKind kind;

    private static final RecordingEventSource[] VALUES = values();

    RecordingEventSource(int id, String label, ProfileKind kind) {
        this.id = id;
        this.label = label;
        this.kind = kind;
    }

    public int getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    /** What a recording of this source becomes once initialized. */
    public ProfileKind kind() {
        return kind;
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
        return kind == ProfileKind.STACK_SAMPLES;
    }
}
