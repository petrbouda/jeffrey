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
 * Where the heap index build has got to, as the panel draws it.
 *
 * <p>Microscope's {@code /heap-dump/init-progress} answers with every stage of the pipeline and its
 * status. The panel does not draw thirteen rows; it draws one line — which stage, of how many, for
 * how long — so this is that line and nothing else. An idle or completed pipeline has no build to
 * show and is represented by {@code null} rather than by a fourth phase, because the panel's answer
 * to both is the same: ask for the profile again and draw whatever figures it now has.
 *
 * @param stageNumber  1-based position of the stage that is running (or, after a failure, the one
 *                     that failed)
 * @param stageCount   how many stages the pipeline has, from the response rather than pinned here
 * @param stageId      the pipeline's own id for that stage, e.g. {@code dominator}
 * @param elapsedMs    time spent so far: finished stages' durations plus the running stage's
 * @param errorMessage what Microscope said went wrong; only set when {@link #failed()}
 */
public record HeapIndexBuild(
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

    private static final String UNKNOWN_FAILURE = "The index build did not finish.";

    /**
     * What each stage is called on the panel. The ids are the pipeline's, the words are for a reader
     * who has never seen it; an id this build has not heard of is shown as itself rather than hidden.
     */
    private static final Map<String, String> STAGE_TITLES = Map.ofEntries(
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
            Map.entry("biggest-collections", "Biggest collections"));

    public boolean failed() {
        return phase == Phase.FAILED;
    }

    public String stageTitle() {
        if (stageId == null) {
            return "Starting";
        }
        return STAGE_TITLES.getOrDefault(stageId, stageId);
    }

    /** Share of stages finished, for a bar that moves per stage rather than per byte. */
    public double fraction() {
        if (stageCount <= 0) {
            return 0;
        }
        return Math.min(1.0, Math.max(0, stageNumber - 1) / (double) stageCount);
    }

    public String failureMessage() {
        return errorMessage == null || errorMessage.isBlank() ? UNKNOWN_FAILURE : errorMessage;
    }
}
