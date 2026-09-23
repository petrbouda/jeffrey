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

package cafe.jeffrey.profile;

import cafe.jeffrey.profile.common.pipeline.PipelineDefinition;

import java.util.List;

/**
 * The profile initialization pipeline: what turning a recording into a profile does, in order.
 * <p>
 * Stage ids are opaque strings shared with the frontend, which owns the labels and decides how they
 * are grouped for display. They match the span names the initialization already emitted, so a run in
 * a JFR recording of Jeffrey itself and a run on screen name their steps the same way.
 */
public final class ProfileInitStages {

    public static final String PIPELINE_ID = "profile-init";

    /** Writes the workspace and project the profile belongs to. Absent for Recordings profiles. */
    public static final String PROFILE_INFO = "profile-info";

    /** Reads the recording and writes its events. Nearly always the longest stage. */
    public static final String PARSE = "parse";

    /** Combines what the parsing threads each saw: event types, threads, active settings. */
    public static final String FLUSH = "flush";

    /**
     * Charges each traced method only for the time no traced method inside it already accounts for.
     * Skipped when the recording traced no methods, which is nearly every recording.
     */
    public static final String METHOD_TRACE_WEIGHTS = "method-trace-weights";

    /** Re-sorts the events table so reads prune by event type and time. */
    public static final String RECLUSTER = "recluster";

    /** Lifts spans out of the events into the trace tables. Skipped when the recording has none. */
    public static final String TRACES = "traces";

    /** Perf counters and other artifacts uploaded beside the recording. */
    public static final String ADDITIONAL_FILES = "additional-files";

    /** Merges the write-ahead log into the database file. */
    public static final String CHECKPOINT = "checkpoint";

    /**
     * Waits for the thread bands and the auto analysis, and writes the findings the analysis
     * produced.
     * <p>
     * The stage covers finishing them, not merely starting them, so a profile that exists at all has
     * the views a reader expects to find already there. What it actually waits for is small: the
     * rule set was started before {@link #PARSE} rather than after this stage, because it reads the
     * recording file and not the database, so by the time the parse is done it usually is too.
     */
    public static final String WARMUP = "warmup";

    public static final PipelineDefinition DEFINITION = new PipelineDefinition(
            PIPELINE_ID,
            List.of(PROFILE_INFO, PARSE, FLUSH, METHOD_TRACE_WEIGHTS, RECLUSTER, TRACES,
                    ADDITIONAL_FILES, CHECKPOINT, WARMUP));

    private ProfileInitStages() {
    }
}
