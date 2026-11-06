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

/**
 * The format a recording file is in, and so which parser reads it and what kind of profile it
 * becomes. A JFR recording is one format whoever wrote it: the JDK's own recorder and
 * async-profiler both write JFR, often into the same file, and are read the same way.
 */
public enum RecordingEventSource {
    JFR("JFR", ProfileKind.JFR),
    HEAP_DUMP("Heap Dump", ProfileKind.HEAP_DUMP),
    PPROF("pprof", ProfileKind.STACK_SAMPLES),
    OPEN_TELEMETRY("OpenTelemetry", ProfileKind.STACK_SAMPLES),
    PERF_COUNTERS("Perf Counters", ProfileKind.PERF_COUNTERS);

    private final String label;
    private final ProfileKind kind;

    RecordingEventSource(String label, ProfileKind kind) {
        this.label = label;
        this.kind = kind;
    }

    /** The format's name in prose. */
    public String getLabel() {
        return label;
    }

    /** What a recording of this source becomes once initialized. */
    public ProfileKind kind() {
        return kind;
    }

    /**
     * Whether this source is an imported stack-sample profile format that Jeffrey only visualizes as
     * flamegraphs ({@link #PPROF}, {@link #OPEN_TELEMETRY}) — as opposed to a {@link #JFR} recording
     * that also feeds the JFR-specific analyses (event viewer, thread viewer, GC, …). Used to skip
     * those analyses for profiles that cannot support them.
     */
    public boolean isFlamegraphOnlyImport() {
        return kind == ProfileKind.STACK_SAMPLES;
    }
}
