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
package cafe.jeffrey.ide.plugin.idea.recording;

import java.util.Map;

/**
 * Where a Microscope pipeline has got to, as the panel draws it.
 *
 * <p>Two pipelines answer this shape: building a heap dump's index, and building a profile out of a
 * recording. Both are the same {@code PipelineProgress} on the wire — every stage with its status and
 * its timing — and the panel draws neither as a list of rows. It draws one line: which stage, of how
 * many, for how long. So this is that line and nothing else.
 *
 * <p>An idle or completed pipeline has no build to show and is represented by {@code null} rather
 * than by a third phase, because the panel's answer to both is the same: ask for the profile again
 * and draw whatever it now has.
 *
 * <p>Which {@link Pipeline} produced it matters for exactly two things — what the stages are called
 * and what to say when one fails — and both live on the enum rather than in a branch here. They have
 * to be told apart: both pipelines have a stage called {@code parse}, so one shared title map would
 * silently label a recording's parse "Parsing objects", which is what a heap dump does.
 *
 * @param stageNumber  1-based position of the stage that is running (or, after a failure, the one
 *                     that failed)
 * @param stageCount   how many stages the pipeline has, from the response rather than pinned here
 * @param stageId      the pipeline's own id for that stage, e.g. {@code dominator}
 * @param elapsedMs    time spent so far: finished stages' durations plus the running stage's
 * @param errorMessage what Microscope said went wrong; only set when {@link #failed()}
 */
public record PipelineBuild(
        Pipeline pipeline,
        Phase phase,
        int stageNumber,
        int stageCount,
        String stageId,
        long elapsedMs,
        String errorMessage) {

    public enum Phase {
        RUNNING,
        FAILED
    }

    /**
     * The two pipelines the panel follows, each carrying the words its own stages go by.
     * <p>
     * The ids are Microscope's; the titles are for a reader who has never seen the pipeline. An id an
     * entry has not heard of is shown as itself rather than hidden — a raw id is a poor label but a
     * true one, and a missing line would leave the reader watching a bar with no explanation.
     */
    public enum Pipeline {

        /** Indexing a heap dump, started from the panel's own <em>Build index</em> button. */
        HEAP_INDEX(
                "The index build did not finish.",
                Map.ofEntries(
                        Map.entry("load", "Loading the dump"),
                        Map.entry("parse", "Parsing objects"),
                        Map.entry("index", "Building the object index"),
                        Map.entry("strings", "String analysis"),
                        Map.entry("dominator", "Dominator tree"),
                        Map.entry("threads", "Threads"),
                        Map.entry("biggest", "Biggest objects"),
                        Map.entry("collections", "Collections"),
                        Map.entry("leaks", "Leak suspects"),
                        Map.entry("classloaders", "Class loaders"),
                        Map.entry("consumers", "Memory consumers"),
                        Map.entry("duplicates", "Duplicate strings"),
                        Map.entry("biggest-collections", "Biggest collections"))),

        /**
         * Turning a recording into a profile. Two of its nine stages are routinely skipped — method
         * trace weights unless the recording traced methods, traces unless it carries spans — which
         * is why a skipped stage has to count as finished rather than as pending.
         */
        PROFILE_INIT(
                "The analysis did not finish.",
                Map.ofEntries(
                        Map.entry("profile-info", "Reading the recording's details"),
                        Map.entry("parse", "Reading the recording"),
                        Map.entry("flush", "Collecting event types and threads"),
                        Map.entry("method-trace-weights", "Method trace weights"),
                        Map.entry("recluster", "Re-sorting events"),
                        Map.entry("traces", "Extracting spans"),
                        Map.entry("additional-files", "Reading the uploaded artifacts"),
                        Map.entry("checkpoint", "Writing the database"),
                        Map.entry("warmup", "Preparing the views")));

        private final String unknownFailure;
        private final Map<String, String> stageTitles;

        Pipeline(String unknownFailure, Map<String, String> stageTitles) {
            this.unknownFailure = unknownFailure;
            this.stageTitles = stageTitles;
        }

        String titleOf(String stageId) {
            if (stageId == null) {
                return "Starting";
            }
            return stageTitles.getOrDefault(stageId, stageId);
        }

        String unknownFailure() {
            return unknownFailure;
        }
    }

    public boolean failed() {
        return phase == Phase.FAILED;
    }

    public String stageTitle() {
        return pipeline.titleOf(stageId);
    }

    /** Share of stages finished, for a bar that moves per stage rather than per byte. */
    public double fraction() {
        if (stageCount <= 0) {
            return 0;
        }
        return Math.min(1.0, Math.max(0, stageNumber - 1) / (double) stageCount);
    }

    public String failureMessage() {
        return errorMessage == null || errorMessage.isBlank() ? pipeline.unknownFailure() : errorMessage;
    }
}
