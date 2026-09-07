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
import static org.junit.Assert.assertTrue;

/**
 * The questions the panel asks a state before drawing it: where "Open in Microscope" lands, and
 * whether the auto analysis is still on its way.
 */
public class RecordingStateTest {

    @Test
    public void landsARecordingOnTheDashboard() {
        assertEquals("dashboard", summary(RecordingState.Kind.RECORDING).landingPath());
    }

    @Test
    public void landsAHeapDumpOnItsOverview() {
        assertEquals("heap-dump/overview", summary(RecordingState.Kind.HEAP_DUMP).landingPath());
    }

    /**
     * A ready profile whose findings have not arrived. The warm-up starts the rule set without
     * waiting for it, so this is the ordinary state for the first seconds after an import.
     */
    @Test
    public void aReadyProfileWithoutItsFindingsIsStillWaitingForThem() {
        assertTrue(state(RecordingState.Status.READY, summary(false, true)).awaitingAnalysis());
    }

    @Test
    public void anAnalysisThatCannotRunIsNotWaitedFor() {
        assertFalse(state(RecordingState.Status.READY, summary(false, false)).awaitingAnalysis());
    }

    @Test
    public void anAnalysisThatLandedIsNotWaitedFor() {
        assertFalse(state(RecordingState.Status.READY, summary(true, true)).awaitingAnalysis());
    }

    /** Nothing is waited for before the profile exists -- there is no analysis to wait on yet. */
    @Test
    public void aProfileStillBeingBuiltIsNotWaitedFor() {
        assertFalse(state(RecordingState.Status.ANALYZING, null).awaitingAnalysis());
    }

    private static RecordingState state(RecordingState.Status status, RecordingState.ProfileSummary summary) {
        return new RecordingState(status, "rec-1", "profile-1", "app.jfr", 1_024L, summary);
    }

    private static RecordingState.ProfileSummary summary(boolean computed, boolean possible) {
        return new RecordingState.ProfileSummary(
                RecordingState.Kind.RECORDING, "profile", null, null, computed, possible,
                List.of(), List.of());
    }

    private static RecordingState.ProfileSummary summary(RecordingState.Kind kind) {
        return new RecordingState.ProfileSummary(kind, "profile", null, null, false, false, List.of(), List.of());
    }
}
