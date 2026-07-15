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

package cafe.jeffrey.profile.manager.model.thread.dump;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.profile.manager.model.thread.dump.ThreadDumpAnalysis.StuckThread;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ThreadDumpAnalyzer")
class ThreadDumpAnalyzerTest {

    private static final RelativeTimeRange TIME_RANGE = new RelativeTimeRange(0, 600_000);

    // A "stuck" worker parked at the same frame, plus a "main" thread whose stack moves between dumps.
    private static String dumpText(String mainFrame) {
        return String.join("\n",
                "\"main\" #1 prio=5 tid=0x01 nid=0x1 runnable",
                "   java.lang.Thread.State: RUNNABLE",
                "\tat " + mainFrame,
                "",
                "\"worker-1\" #12 prio=5 tid=0x02 nid=0x2 waiting on condition",
                "   java.lang.Thread.State: WAITING (parking)",
                "\tat jdk.internal.misc.Unsafe.park(Native Method)",
                "");
    }

    private static List<RawDump> threeDumps() {
        return List.of(
                new RawDump(0, dumpText("app.Main.a(Main.java:1)")),
                new RawDump(60_000, dumpText("app.Main.b(Main.java:2)")),
                new RawDump(120_000, dumpText("app.Main.c(Main.java:3)")));
    }

    @Test
    @DisplayName("Builds per-state timeline series and ranks top frames")
    void timelineAndFrames() {
        ThreadDumpAnalysis analysis = ThreadDumpAnalyzer.analyze(threeDumps(), TIME_RANGE);

        assertEquals(3, analysis.header().dumpCount());
        assertEquals(2, analysis.header().peakThreadCount());

        List<String> seriesNames = analysis.stateTimeline().series().stream().map(s -> s.name()).toList();
        assertTrue(seriesNames.contains("RUNNABLE"));
        assertTrue(seriesNames.contains("WAITING"));

        // Unsafe.park is the top frame in all 3 dumps (one distinct thread).
        var topFrame = analysis.topFrames().getFirst();
        assertEquals("jdk.internal.misc.Unsafe.park(Native Method)", topFrame.frame());
        assertEquals(3, topFrame.occurrences());
        assertEquals(1, topFrame.distinctThreads());
    }

    @Test
    @DisplayName("Flags a thread stuck at the same stack across consecutive dumps")
    void detectsStuckThreads() {
        ThreadDumpAnalysis analysis = ThreadDumpAnalyzer.analyze(threeDumps(), TIME_RANGE);

        List<StuckThread> stuck = analysis.stuckThreads();
        assertEquals(1, stuck.size());
        StuckThread worker = stuck.getFirst();
        assertEquals("worker-1", worker.name());
        assertEquals(3, worker.consecutiveDumps());
        assertEquals(120_000, worker.stuckForMillis());

        // "main" changes frame every dump, so it is not stuck.
        assertFalse(stuck.stream().anyMatch(s -> s.name().equals("main")));
    }

    @Test
    @DisplayName("Ranks lock contention from the worst dump")
    void ranksLockContention() {
        String contended = String.join("\n",
                "\"holder\" #1 prio=5 tid=0x01 nid=0x1 runnable",
                "   java.lang.Thread.State: RUNNABLE",
                "\tat app.Holder.run(Holder.java:1)",
                "\t- locked <0x000000aa> (a app.Lock)",
                "",
                "\"waiter-1\" #2 prio=5 tid=0x02 nid=0x2 waiting for monitor entry",
                "   java.lang.Thread.State: BLOCKED (on object monitor)",
                "\tat app.Waiter.run(Waiter.java:1)",
                "\t- waiting to lock <0x000000aa> (a app.Lock)",
                "",
                "\"waiter-2\" #3 prio=5 tid=0x03 nid=0x3 waiting for monitor entry",
                "   java.lang.Thread.State: BLOCKED (on object monitor)",
                "\tat app.Waiter.run(Waiter.java:1)",
                "\t- waiting to lock <0x000000aa> (a app.Lock)",
                "");

        ThreadDumpAnalysis analysis = ThreadDumpAnalyzer.analyze(List.of(new RawDump(0, contended)), TIME_RANGE);

        assertEquals(1, analysis.lockContention().size());
        var contention = analysis.lockContention().getFirst();
        assertEquals("0x000000aa", contention.monitorId());
        assertEquals("app.Lock", contention.monitorClass());
        assertEquals(2, contention.waiterCount());
        assertEquals("holder", contention.owner());
    }

    @Test
    @DisplayName("Builds a heatmap with one row per tracked thread aligned to dumps")
    void buildsHeatmap() {
        ThreadDumpAnalysis analysis = ThreadDumpAnalyzer.analyze(threeDumps(), TIME_RANGE);

        assertEquals(3, analysis.heatmap().dumpOffsets().size());
        assertFalse(analysis.heatmap().rows().isEmpty());
        analysis.heatmap().rows().forEach(row -> assertEquals(3, row.states().size()));
    }
}
