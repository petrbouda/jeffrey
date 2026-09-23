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

package cafe.jeffrey.ide.plugin.idea.recording;

import java.nio.file.Path;

/**
 * A recording in this project that could be the baseline, and what Microscope holds for it.
 *
 * <p>Carrying the state rather than only the path is what lets the menu say which of them can be
 * compared right now and how long each one ran. Picking a baseline blind is how a reader ends up
 * subtracting a four-minute run from a twenty-minute one and believing the result; the figures are
 * in the menu so that choice is informed before Microscope has to caution about it.
 *
 * @param file  where the file is on disk, which is how a pick is sent back to the panel
 * @param state what Microscope answered for it, from the same by-path lookup the panel itself makes
 */
public record CompareCandidate(Path file, RecordingState state) {

    /** A profile that exists and can be subtracted today, without an import first. */
    public boolean isReady() {
        return state.status() == RecordingState.Status.READY;
    }

    public String filename() {
        return state.filename();
    }

    /**
     * The figures the menu prints beside a ready recording — its window and how many samples it
     * holds, the two that decide whether a pair is worth comparing at all.
     */
    public String figures() {
        RecordingState.ProfileSummary summary = state.summary();
        if (summary == null || summary.recording() == null) {
            return "";
        }
        RecordingState.RecordingFigures recording = summary.recording();
        return Formats.duration(recording.durationInMillis())
                + " · " + Formats.count(recording.sampleCount());
    }
}
