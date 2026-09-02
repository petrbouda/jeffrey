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
     * Starts the thread bands. The stage covers starting them, not
     * finishing them: they are caches, and the profile is usable without them.
     */
    public static final String WARMUP = "warmup";

    public static final PipelineDefinition DEFINITION = new PipelineDefinition(
            PIPELINE_ID,
            List.of(PROFILE_INFO, PARSE, FLUSH, METHOD_TRACE_WEIGHTS, RECLUSTER, TRACES,
                    ADDITIONAL_FILES, CHECKPOINT, WARMUP));

    private ProfileInitStages() {
    }
}
