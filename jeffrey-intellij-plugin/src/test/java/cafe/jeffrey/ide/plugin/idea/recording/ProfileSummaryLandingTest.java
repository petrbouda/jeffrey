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

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * What the profile's kind decides: where "Open in Microscope" lands, which views the tiles are drawn
 * from, and whether the panel has anything to show at all.
 *
 * <p>The bare profile URL redirects to the JFR dashboard without looking at what the profile is, so
 * the kind is spelled into the link on this side. The same kind gates the figures: a dump with no
 * index can answer none of them.
 */
public class ProfileSummaryLandingTest {

    private static final String RECORDING_LANDING = "dashboard";
    private static final String HEAP_DUMP_LANDING = "heap-dump/overview";

    private static final String PROFILE_NAME = "run.jfr";

    private static final RecordingState.HeapFigures INDEXED =
            new RecordingState.HeapFigures(1_024L, 32L, 8, 4, true);
    private static final RecordingState.HeapFigures NOT_INDEXED =
            new RecordingState.HeapFigures(0L, 0L, 0, 0, false);

    @Test
    public void landsARecordingOnTheDashboard() {
        assertEquals(RECORDING_LANDING, recording().landingPath());
    }

    @Test
    public void landsAHeapDumpOnItsOverview() {
        assertEquals(HEAP_DUMP_LANDING, heapDump(INDEXED).landingPath());
    }

    /** An un-indexed dump still lands on the overview — the index decides the figures, not the page. */
    @Test
    public void landsAnUnindexedHeapDumpThereToo() {
        assertEquals(HEAP_DUMP_LANDING, heapDump(null).landingPath());
    }

    @Test
    public void drawsItsTilesFromTheViewsOfItsKind() {
        assertSame(ProfileView.RECORDING, recording().views());
        assertSame(ProfileView.HEAP, heapDump(INDEXED).views());
    }

    /**
     * A dump that has not been indexed: every tile would open an empty page and there are no figures
     * to print, so the panel locks the grid and offers the build instead.
     */
    @Test
    public void reportsAHeapDumpWithoutFiguresAsIndexMissing() {
        assertTrue(heapDump(null).indexMissing());
        assertTrue(heapDump(NOT_INDEXED).indexMissing());
    }

    @Test
    public void reportsAnIndexedHeapDumpAsReady() {
        assertFalse(heapDump(INDEXED).indexMissing());
    }

    /** A recording has no index to be missing, whatever its figures say. */
    @Test
    public void neverReportsARecordingAsIndexMissing() {
        assertFalse(recording().indexMissing());
        assertFalse(heapFiguresOnARecording().indexMissing());
    }

    @Test
    public void tellsTheTwoKindsApart() {
        assertTrue(heapDump(INDEXED).isHeapDump());
        assertFalse(recording().isHeapDump());
    }

    /** Only a ready dump can offer the build — nothing else is in a state to run one. */
    @Test
    public void offersTheIndexBuildOnlyForAReadyUnindexedDump() {
        assertTrue(state(RecordingState.Status.READY, heapDump(null)).needsHeapIndex());
        assertFalse(state(RecordingState.Status.READY, heapDump(INDEXED)).needsHeapIndex());
        assertFalse(state(RecordingState.Status.ANALYZING, heapDump(null)).needsHeapIndex());
        assertFalse(state(RecordingState.Status.READY, recording()).needsHeapIndex());
        assertFalse(state(RecordingState.Status.READY, null).needsHeapIndex());
    }

    private static RecordingState.ProfileSummary recording() {
        return summary(RecordingState.Kind.RECORDING, null);
    }

    private static RecordingState.ProfileSummary heapDump(RecordingState.HeapFigures heap) {
        return summary(RecordingState.Kind.HEAP_DUMP, heap);
    }

    /** The shape Microscope cannot send, kept honest: heap figures under a recording change nothing. */
    private static RecordingState.ProfileSummary heapFiguresOnARecording() {
        return summary(RecordingState.Kind.RECORDING, NOT_INDEXED);
    }

    private static RecordingState.ProfileSummary summary(
            RecordingState.Kind kind, RecordingState.HeapFigures heap) {
        return new RecordingState.ProfileSummary(kind, PROFILE_NAME, null, heap, false, false, List.of(), List.of());
    }

    private static RecordingState state(RecordingState.Status status, RecordingState.ProfileSummary summary) {
        return new RecordingState(status, "recording-1", "profile-1", PROFILE_NAME, 1_024L, summary);
    }
}
