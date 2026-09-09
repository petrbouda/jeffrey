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
 * The panel's verdict on a pair, which is the one thing it says before a reader opens a comparison.
 *
 * <p>Worth pinning because the failure it guards against is silent: any two recordings subtract
 * cleanly, and the result always looks like a finding. A caution that stops firing does not break
 * anything — it just lets a twenty-minute run be measured against a four-minute one, and the reader
 * believes the answer.
 */
public class ComparabilityTest {

    private static final long MINUTE = 60_000L;

    @Test
    public void aMatchedPairIsComparable() {
        Comparability verdict = Comparability.of(
                recording(16 * MINUTE, 99), recording(16 * MINUTE, 99));

        assertEquals(Comparability.Level.COMPARABLE, verdict.level());
        assertFalse(verdict.isCautioned());
        assertFalse(verdict.windowsDiffer());
        assertFalse(verdict.eventTypesDiffer());
    }

    /**
     * Two runs of the same workload never land on the same second. A tolerance of zero would caution
     * about every honest pair, and a caution that fires every time is one nobody reads.
     */
    @Test
    public void smallDifferencesInLengthAreNotWorthACaution() {
        Comparability verdict = Comparability.of(
                recording(16 * MINUTE, 99), recording(15 * MINUTE, 99));

        assertEquals(Comparability.Level.COMPARABLE, verdict.level());
    }

    /** The case the caution exists for: a benchmark's baseline a quarter of the primary's length. */
    @Test
    public void aFourfoldDifferenceInLengthIsCautioned() {
        Comparability verdict = Comparability.of(
                recording(16 * MINUTE, 99), recording(4 * MINUTE, 99));

        assertEquals(Comparability.Level.CAUTION, verdict.level());
        assertTrue(verdict.windowsDiffer());
        assertFalse(verdict.eventTypesDiffer());
        assertTrue(verdict.detail(), verdict.detail().contains("16 m"));
        assertTrue(verdict.detail(), verdict.detail().contains("4 m"));
        // The reading that saves a benchmark comparison from being wrong.
        assertTrue(verdict.detail(), verdict.detail().contains("share"));
    }

    /** Direction changes nothing here: a fourfold difference is one whichever way round it is read. */
    @Test
    public void theSamePairIsJudgedTheSameWayRound() {
        Comparability forwards = Comparability.of(recording(16 * MINUTE, 99), recording(4 * MINUTE, 99));
        Comparability backwards = Comparability.of(recording(4 * MINUTE, 99), recording(16 * MINUTE, 99));

        assertEquals(forwards.level(), backwards.level());
        assertEquals(forwards.windowsDiffer(), backwards.windowsDiffer());
    }

    /**
     * A type on one side only is a profiler-settings difference, and reporting it as work that
     * appeared or vanished is the mistake compare-jfr warns about in as many words.
     */
    @Test
    public void differentEventTypeCountsAreCautionedAsAProfilerDifference() {
        Comparability verdict = Comparability.of(
                recording(16 * MINUTE, 99), recording(16 * MINUTE, 87));

        assertEquals(Comparability.Level.CAUTION, verdict.level());
        assertTrue(verdict.eventTypesDiffer());
        assertFalse(verdict.windowsDiffer());
        assertTrue(verdict.detail(), verdict.detail().contains("99"));
        assertTrue(verdict.detail(), verdict.detail().contains("87"));
        assertTrue(verdict.detail(), verdict.detail().contains("profiler"));
    }

    @Test
    public void bothProblemsAtOnceAreSaidInOneVerdict() {
        Comparability verdict = Comparability.of(
                recording(16 * MINUTE, 99), recording(4 * MINUTE, 87));

        assertTrue(verdict.windowsDiffer());
        assertTrue(verdict.eventTypesDiffer());
        assertTrue(verdict.detail(), verdict.detail().contains("share"));
        assertTrue(verdict.detail(), verdict.detail().contains("profiler"));
    }

    /** A dump compares with a dump, on a page with different figures entirely. */
    @Test
    public void aHeapDumpCannotBeComparedWithARecording() {
        Comparability verdict = Comparability.of(recording(16 * MINUTE, 99), heapDump());

        assertEquals(Comparability.Level.INCOMPARABLE, verdict.level());
        assertTrue(verdict.isCautioned());
        assertTrue(verdict.detail(), verdict.detail().contains("heap dump"));
        // Nothing is known to differ, so nothing gets coloured as if it did.
        assertFalse(verdict.windowsDiffer());
        assertFalse(verdict.eventTypesDiffer());
    }

    @Test
    public void aProfileWithNoFiguresLeavesNothingToWeigh() {
        Comparability verdict = Comparability.of(recording(16 * MINUTE, 99), withoutSummary());

        assertEquals(Comparability.Level.INCOMPARABLE, verdict.level());
    }

    /**
     * An unreported window is not evidence of a mismatch. Two of them pass; one against a real
     * window does not, because then the pair genuinely cannot be weighed on length.
     */
    @Test
    public void unreportedWindowsAreNotTreatedAsADifference() {
        assertEquals(
                Comparability.Level.COMPARABLE,
                Comparability.of(recording(0, 99), recording(0, 99)).level());
        assertTrue(Comparability.of(recording(16 * MINUTE, 99), recording(0, 99)).windowsDiffer());
    }

    // --- fixtures -------------------------------------------------------------------------------

    private static RecordingState recording(long durationMillis, int eventTypes) {
        RecordingState.ProfileSummary summary = new RecordingState.ProfileSummary(
                RecordingState.Kind.RECORDING,
                "run",
                new RecordingState.RecordingFigures(durationMillis, 5_000_000L, eventTypes, 0L, 0L),
                null,
                true,
                true,
                List.of(),
                List.of());
        return new RecordingState(
                RecordingState.Status.READY, "rec", "profile", "run.jfr", 1024L, summary);
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
                RecordingState.Status.READY, "rec", "profile", "dump.hprof", 1024L, summary);
    }

    private static RecordingState withoutSummary() {
        return new RecordingState(
                RecordingState.Status.READY, "rec", "profile", "run.jfr", 1024L, null);
    }
}
