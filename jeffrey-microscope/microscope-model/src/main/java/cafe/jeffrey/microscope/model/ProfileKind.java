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

package cafe.jeffrey.microscope.model;

/**
 * What a recording turns into once it is initialized: the shape of its profile, which decides how it
 * is built and which Profile Detail opens it.
 * <p>
 * Each initializable recording is exactly one of these, and a profile is built from one recording of
 * one kind. A heap dump is never part of a JFR profile, nor perf counters: in a bundle each is its own
 * recording with its own profile.
 */
public enum ProfileKind {

    /** A JFR recording (JDK or async-profiler): events parsed into the profile database. */
    JFR,

    /** An imported stack-sample profile (pprof, OpenTelemetry): flamegraphs only. */
    STACK_SAMPLES,

    /** An HPROF heap dump: an index and cached analyses beside the dump, no events. */
    HEAP_DUMP,

    /** A HotSpot perf-data snapshot (hsperfdata): the JVM's counters at one moment, no events. */
    PERF_COUNTERS
}
