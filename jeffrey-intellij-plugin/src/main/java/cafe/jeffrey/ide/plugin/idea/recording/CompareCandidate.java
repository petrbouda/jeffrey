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
