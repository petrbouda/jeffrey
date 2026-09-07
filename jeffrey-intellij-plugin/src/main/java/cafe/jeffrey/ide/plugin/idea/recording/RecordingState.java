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

import cafe.jeffrey.ide.plugin.idea.AnalysableFiles;

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
        ProfileSummary summary,
        HeapIndexBuild indexBuild) {

    /** The state as Microscope reports it, before the panel has asked about any index build. */
    public RecordingState(
            Status status,
            String recordingId,
            String profileId,
            String filename,
            long sizeInBytes,
            ProfileSummary summary) {
        this(status, recordingId, profileId, filename, sizeInBytes, summary, null);
    }

    /**
     * The same state with the index build the panel is watching attached, or detached when
     * {@code build} is null. The by-path answer does not carry the build — that is a second call the
     * panel makes only for a dump whose index is missing — so it is joined on here.
     */
    public RecordingState withIndexBuild(HeapIndexBuild build) {
        return new RecordingState(status, recordingId, profileId, filename, sizeInBytes, summary, build);
    }

    /**
     * Whether this file is a heap dump. Microscope's answer when there is one, the file name until
     * then — the two agree, because Microscope decides by the same name.
     */
    public boolean isHeapDumpFile() {
        if (summary != null) {
            return summary.isHeapDump();
        }
        return AnalysableFiles.isHeapDumpName(filename);
    }

    /** A ready heap dump whose index has not been built: the one state that can offer the build. */
    public boolean needsHeapIndex() {
        return status == Status.READY && summary != null && summary.indexMissing();
    }

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

    /** What the profile holds, which decides the figures, the tiles and the agent's skill. */
    public enum Kind {
        RECORDING,
        HEAP_DUMP;

        static Kind of(String wireCode) {
            return HEAP_DUMP.name().equals(wireCode) ? HEAP_DUMP : RECORDING;
        }
    }

    /**
     * The figures the panel is allowed to show — the whole list, deliberately. The plugin does not
     * render profile data; four numbers and the findings are the narrow exception, and anything a
     * reader would need a chart for stays in Microscope.
     *
     * <p>Exactly one of {@code recording} and {@code heap} is set. A dump has no recording window and
     * a recording has no retained size; one flat row of nullable numbers would leave the panel
     * guessing which of them mean anything.
     */
    public record ProfileSummary(
            Kind kind,
            String profileName,
            RecordingFigures recording,
            HeapFigures heap,
            boolean analysisComputed,
            List<Finding> findings,
            List<String> disabledFeatures) {

        /** Where a recording opens: the JFR summary dashboard. */
        private static final String RECORDING_LANDING = "dashboard";

        /** Where a dump opens: its overview. A dump has no dashboard, and Microscope's bare profile URL leads there. */
        private static final String HEAP_DUMP_LANDING = "heap-dump/overview";

        public ProfileSummary {
            findings = findings == null ? List.of() : List.copyOf(findings);
            disabledFeatures = disabledFeatures == null ? List.of() : List.copyOf(disabledFeatures);
        }

        public boolean isHeapDump() {
            return kind == Kind.HEAP_DUMP;
        }

        /**
         * A dump whose index has not been built. Nothing below the header can answer yet: no
         * figures, and every view tile opens an empty page, so the panel offers the build and locks
         * the tiles until it is done.
         */
        public boolean indexMissing() {
            return isHeapDump() && (heap == null || !heap.cacheReady());
        }

        /**
         * The page "Open in Microscope" lands on, as a sub-path under the profile.
         *
         * <p>Named here rather than left to Microscope's bare profile URL, because that URL redirects
         * to the JFR dashboard without looking at what the profile is — which for a dump is a page
         * about data it does not have. The kind is known on this side, so the link says where it goes.
         */
        public String landingPath() {
            return isHeapDump() ? HEAP_DUMP_LANDING : RECORDING_LANDING;
        }

        /** The views this profile's tiles are drawn from. */
        public List<ProfileView> views() {
            return isHeapDump() ? ProfileView.HEAP : ProfileView.RECORDING;
        }
    }

    public record RecordingFigures(
            long durationInMillis,
            long sampleCount,
            int eventTypeCount,
            long capturedSamples,
            long lostSamples) {

        /** Share of samples the kernel dropped, or -1 when the recording reports no sampler health. */
        public double lossRatio() {
            long total = capturedSamples + lostSamples;
            if (total <= 0) {
                return -1;
            }
            return (double) lostSamples / total;
        }
    }

    /**
     * @param cacheReady whether the dump has been indexed. An un-indexed dump can answer nothing, so
     *                   the panel says so rather than printing four zeroes as if they were facts
     */
    public record HeapFigures(
            long totalBytes,
            long totalInstances,
            int classCount,
            int gcRootCount,
            boolean cacheReady) {
    }

    public record Finding(String rule, String severity, String summary) {

        /**
         * The severity the auto-analysis reports for a finding worth colouring. It is an enum name on
         * the wire, from AutoAnalysisResult, which is why it is spelled once here rather than at the
         * comparison.
         */
        private static final String SEVERITY_WARNING = "WARNING";

        public boolean isWarning() {
            return SEVERITY_WARNING.equals(severity);
        }
    }

    public static RecordingState unavailable(String filename, long sizeInBytes) {
        return new RecordingState(Status.UNAVAILABLE, null, null, filename, sizeInBytes, null, null);
    }
}
