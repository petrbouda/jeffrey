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

import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * What the panel is showing, and — while it is showing a pair — which of the two is which.
 */
public class PanelStateTest {

    private static final Path BASELINE_FILE = Path.of("/recordings/before.jfr");

    @Test
    public void aTabOpensWithNothingToCompareAgainst() {
        PanelState state = PanelState.of(ready("after.jfr"));

        assertFalse(state.hasBaseline());
        assertFalse(state.isComparing());
        assertNull(state.comparability());
        assertTrue(state.candidates().isEmpty());
    }

    @Test
    public void aReadyPairCanBeCompared() {
        PanelState state = PanelState.of(ready("after.jfr")).withBaseline(ready("before.jfr"));

        assertTrue(state.hasBaseline());
        assertTrue(state.isComparing());
        assertEquals(Comparability.Level.COMPARABLE, state.comparability().level());
    }

    /**
     * A baseline still importing is shown but opens nothing: it has no profile to subtract yet, and
     * a link naming it would land on an error.
     */
    @Test
    public void aBaselineStillImportingIsHeldButNotCompared() {
        PanelState state = PanelState.of(ready("after.jfr")).withBaseline(importing("before.jfr"));

        assertTrue(state.hasBaseline());
        assertFalse(state.isComparing());
        assertNull(state.comparability());
        // and the tiles stay the profile's own, because the differential pages cannot answer yet
        assertEquals(ProfileView.RECORDING, state.views());
    }

    /** The whole point of the state: while comparing, the tiles are the two differential views. */
    @Test
    public void comparingSwapsTheTilesForTheDifferentialViews() {
        PanelState state = PanelState.of(ready("after.jfr")).withBaseline(ready("before.jfr"));

        assertEquals(ProfileView.DIFFERENTIAL, state.views());
        assertTrue(state.views().stream().allMatch(view -> view.path().contains("differential")));
    }

    /**
     * The direction never moves: the tab's own recording is the primary. Read the other way round,
     * every regression the comparison reports would come back as an improvement.
     */
    @Test
    public void theTabsOwnRecordingIsAlwaysThePrimary() {
        RecordingState primary = ready("after.jfr");
        RecordingState baseline = ready("before.jfr");

        PanelState state = PanelState.of(primary).withBaseline(baseline);

        assertEquals(primary, state.recording());
        assertEquals(baseline, state.baseline());
    }

    @Test
    public void updatingOneSideKeepsTheOther() {
        PanelState state = PanelState.of(ready("after.jfr"))
                .withBaseline(ready("before.jfr"))
                .withCandidates(List.of(new CompareCandidate(BASELINE_FILE, ready("before.jfr"))));

        PanelState polled = state.withRecording(ready("after.jfr"));

        assertTrue(polled.isComparing());
        assertEquals(1, polled.candidates().size());
    }

    /** A heap dump is never compared here, whatever it is paired with. */
    @Test
    public void aDumpPairedWithARecordingReportsItselfIncomparable() {
        PanelState state = PanelState.of(ready("after.jfr")).withBaseline(heapDump());

        assertTrue(state.isComparing());
        assertEquals(Comparability.Level.INCOMPARABLE, state.comparability().level());
    }

    /**
     * A pair the verdict cannot judge keeps the primary's own views.
     *
     * <p>Otherwise the panel says "these two cannot be compared" and, in the same render, replaces
     * every tile with the differential pages it just said cannot be drawn — and takes the primary's
     * own views away with them.
     */
    @Test
    public void anIncomparablePairDoesNotRewireTheLinks() {
        PanelState state = PanelState.of(ready("after.jfr")).withBaseline(heapDump());

        assertTrue(state.isComparing());
        assertFalse(state.opensComparison());
        assertEquals(ProfileView.RECORDING, state.views());
    }

    @Test
    public void aComparablePairOpensTheComparison() {
        PanelState state = PanelState.of(ready("after.jfr")).withBaseline(ready("before.jfr"));

        assertTrue(state.opensComparison());
    }

    @Test
    public void aBaselineStillImportingOpensNothing() {
        PanelState state = PanelState.of(ready("after.jfr")).withBaseline(importing("before.jfr"));

        assertFalse(state.opensComparison());
    }

    // --- fixtures -------------------------------------------------------------------------------

    private static RecordingState ready(String filename) {
        RecordingState.ProfileSummary summary = new RecordingState.ProfileSummary(
                RecordingState.Kind.RECORDING,
                filename,
                new RecordingState.RecordingFigures(600_000L, 1_000L, 42, 0L, 0L),
                null,
                true,
                true,
                List.of(),
                List.of());
        return new RecordingState(
                RecordingState.Status.READY, "rec", "profile-" + filename, filename, 1024L, summary);
    }

    private static RecordingState importing(String filename) {
        return new RecordingState(
                RecordingState.Status.ANALYZING, "rec", null, filename, 1024L, null);
    }

    private static RecordingState heapDump() {
        RecordingState.ProfileSummary summary = new RecordingState.ProfileSummary(
                RecordingState.Kind.HEAP_DUMP,
                "dump",
                null,
                new RecordingState.HeapFigures(1024L, 10L, 5, 2, true),
                true,
                true,
                List.of(),
                List.of());
        return new RecordingState(
                RecordingState.Status.READY, "rec", "profile-dump", "dump.hprof", 1024L, summary);
    }
}
