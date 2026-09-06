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

import java.util.List;

/**
 * What Microscope reports about one recording file, as the panel needs it.
 *
 * <p>Mirrors {@code IdeRecordingStateResponse} on the Microscope side. The duplication is the same
 * one {@link cafe.jeffrey.ide.plugin.idea.AnalysableFiles} carries: this plugin is a separate build
 * and cannot see that record. Keeping the shape to one endpoint is what makes the duplication small
 * enough to live with — the alternative was four endpoints' worth of DTOs pinned in here.
 */
public record RecordingState(
        Status status,
        String recordingId,
        String profileId,
        String filename,
        long sizeInBytes,
        ProfileSummary summary) {

    public enum Status {
        /** Microscope has never seen this file. */
        NOT_IMPORTED,
        /** Imported, but never analysed — or its profile was deleted afterwards. */
        IMPORTED,
        /** A profile exists and is still being built. */
        ANALYZING,
        /** A profile exists and can be opened. */
        READY,
        /** Microscope could not be reached, or answered something this plugin does not understand. */
        UNAVAILABLE;

        /**
         * An unrecognised code reads as {@link #UNAVAILABLE} rather than throwing. A newer Microscope
         * that grows a state this plugin has not heard of should leave the panel saying it cannot
         * describe the file, not leave the tab blank with an exception in the log.
         */
        static Status of(String wireCode) {
            if (wireCode == null) {
                return UNAVAILABLE;
            }
            for (Status status : values()) {
                if (status.name().equals(wireCode)) {
                    return status;
                }
            }
            return UNAVAILABLE;
        }
    }

    /**
     * The figures the panel is allowed to show — the whole list, deliberately. The plugin does not
     * render profile data; these four numbers and the findings are the narrow exception, and anything
     * a reader would need a chart for stays in Microscope.
     */
    public record ProfileSummary(
            String profileName,
            long durationInMillis,
            long sampleCount,
            int eventTypeCount,
            long capturedSamples,
            long lostSamples,
            boolean analysisComputed,
            List<Finding> findings,
            List<String> disabledFeatures) {

        public ProfileSummary {
            findings = findings == null ? List.of() : List.copyOf(findings);
            disabledFeatures = disabledFeatures == null ? List.of() : List.copyOf(disabledFeatures);
        }

        /** Share of samples the kernel dropped, or -1 when the recording reports no sampler health. */
        public double lossRatio() {
            long total = capturedSamples + lostSamples;
            if (total <= 0) {
                return -1;
            }
            return (double) lostSamples / total;
        }
    }

    public record Finding(String rule, String severity, String summary) {

        public boolean isWarning() {
            return "WARNING".equals(severity);
        }
    }

    public static RecordingState unavailable(String filename, long sizeInBytes) {
        return new RecordingState(Status.UNAVAILABLE, null, null, filename, sizeInBytes, null);
    }
}
