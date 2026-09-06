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

package cafe.jeffrey.microscope.core.web.dto.response;

import java.util.List;

/**
 * What Microscope knows about a recording file sitting on the developer's disk.
 *
 * <p>Answers the one question the IDE panel opens with — "have I seen this file, and did it produce
 * anything?" — and, when the answer is yes, carries the handful of figures the panel shows so it does
 * not have to assemble them from four endpoints. That assembly is the reason this exists rather than
 * the panel calling {@code /recordings}, {@code /profiles/{id}}, {@code /sampler-health} and
 * {@code /analysis} in turn: four round trips over localhost is the smaller cost, four DTO shapes
 * pinned into a separately built plugin is the larger one.
 *
 * @param state       one of {@link State}, as its name
 * @param recordingId null until the file has been imported
 * @param profileId   null until the recording has been analysed
 * @param summary     null unless {@code state} is {@link State#READY}
 */
public record IdeRecordingStateResponse(
        String state,
        String recordingId,
        String profileId,
        String filename,
        long sizeInBytes,
        ProfileSummary summary) {

    /**
     * The panel has four things to draw, so the contract names four states rather than leaving the
     * plugin to infer them from which ids came back null.
     */
    public enum State {
        /** Microscope has never seen this file. */
        NOT_IMPORTED,
        /** Imported, but never analysed — or its profile was deleted afterwards. */
        IMPORTED,
        /** A profile exists and its initialization pipeline is still running. */
        ANALYZING,
        /** A profile exists and can be opened. */
        READY
    }

    /**
     * The figures the IDE panel is allowed to show. Deliberately a closed list rather than a slice of
     * the profile: the plugin does not render profile data, and this is the narrow exception — how
     * long the recording covers, how much it holds, whether the samples can be trusted, and what
     * auto-analysis flagged. Anything a reader would need a chart for stays in Microscope.
     *
     * @param sampleCount    total samples across every recorded event type
     * @param eventTypeCount how many event types the recording carries
     * @param lostSamples    samples the kernel dropped, 0 when the recording reports no loss at all
     * @param findings         auto-analysis findings, most severe first; empty when never computed
     * @param disabledFeatures {@code FeatureType} names this profile has no data for, so the IDE can
     *                         say a view is empty instead of linking the reader to an empty page
     */
    public record ProfileSummary(
            String profileName,
            Long profilingStartedAt,
            Long profilingFinishedAt,
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
    }

    /** One auto-analysis finding, flattened to what a one-line row in the IDE can show. */
    public record Finding(String rule, String severity, String summary) {
    }

    public static IdeRecordingStateResponse notImported(String filename, long sizeInBytes) {
        return new IdeRecordingStateResponse(
                State.NOT_IMPORTED.name(), null, null, filename, sizeInBytes, null);
    }
}
