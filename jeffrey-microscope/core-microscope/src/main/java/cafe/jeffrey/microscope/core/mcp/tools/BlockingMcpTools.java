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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.core.mcp.LinkedOutput;
import cafe.jeffrey.microscope.core.mcp.UiLinks;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.model.blocking.BlockingOverview;
import cafe.jeffrey.profile.manager.model.blocking.ContentionStat;
import cafe.jeffrey.profile.manager.model.blocking.MonitorWaitStat;
import cafe.jeffrey.profile.manager.model.blocking.PinnedThreadEntry;
import org.springframework.ai.tool.annotation.Tool;

import java.util.List;

/**
 * Threads that were waiting rather than working: contended monitors, waits, parks, sleeps, and a
 * virtual thread pinned to its carrier.
 * <p>
 * {@code flamegraph_export} on {@code jdk.JavaMonitorEnter} is the nearest existing answer and it
 * loses two things this keeps — the aggregation per monitor class, which is what names the lock
 * rather than the call site, and pinning, which has no flamegraph at all.
 */
public class BlockingMcpTools {

    private static final String BLOCKING_VIEW = "blocking-operations";
    private static final String VIRTUAL_THREADS_VIEW = "virtual-threads";

    private static final int MAX_ROWS = 40;

    private static final String NO_BLOCKING_DATA =
            "This profile recorded no blocking events - no monitor contention, waits, parks or sleeps. "
                    + "These event types are threshold-gated, so a recording can hold none because "
                    + "nothing blocked for long enough as well as because the profiler was not asked "
                    + "for them.";

    private static final String NO_MONITOR_DATA =
            "This profile recorded no jdk.JavaMonitorEnter or jdk.JavaMonitorWait events, so there is "
                    + "no per-monitor contention to report. blocking_overview says which blocking event "
                    + "types the recording does carry.";

    private static final String NO_PINNED_DATA =
            "This profile recorded no jdk.VirtualThreadPinned events. Either the application does not "
                    + "use virtual threads, or none of them pinned their carrier - blocking_overview "
                    + "distinguishes the two by saying whether the event type was recorded at all.";

    private static final String STEP_MONITORS =
            "blocking_monitors aggregates per monitor class, which is what names the lock rather than "
                    + "the call site that happened to hit it.";
    private static final String STEP_FRAMES =
            "For the call paths that reached a lock, flamegraph_export with jdk.JavaMonitorEnter and "
                    + "useWeight true - the weight is nanoseconds blocked.";
    private static final String STEP_PINNED =
            "This recording has pinned virtual threads: blocking_pinnedThreads names them and for how "
                    + "long. A pinned carrier blocks every other virtual thread scheduled on it.";
    private static final String STEP_PIN_CAUSE =
            "A pin comes from a synchronized block or a native call on the carrier. Read that code "
                    + "before proposing the fix; the profile names the thread, not the reason.";
    private static final String STEP_OVERVIEW =
            "blocking_overview puts these figures beside the waits, parks and sleeps.";

    private final ProfileManager profileManager;

    public BlockingMcpTools(ProfileManager profileManager) {
        this.profileManager = profileManager;
    }

    @Tool(description = "Where threads waited instead of running: contended monitors and the total time "
            + "blocked on them, how many waits, parks and sleeps there were, and how often a virtual "
            + "thread pinned its carrier. Answers 'the CPU is idle and it is still slow'. Each figure "
            + "comes with whether its event type was recorded at all, so an absent one is not read as "
            + "a zero.")
    public String overview() {
        BlockingOverview overview = profileManager.blockingManager().overview();
        if (nothingRecorded(overview)) {
            return NO_BLOCKING_DATA;
        }

        return LinkedOutput.json(new BlockingDashboard(
                overview,
                NextSteps.builder()
                        .add(STEP_MONITORS)
                        .when(overview.pinnedCount() > 0, STEP_PINNED)
                        .add(STEP_FRAMES)
                        .build(),
                UiLinks.view(profileId(), BLOCKING_VIEW)));
    }

    @Tool(description = "Monitor contention aggregated per lock: the class of each monitor, how many "
            + "times threads blocked on it, for how long in total and at worst, and how many distinct "
            + "threads were involved. A lock held briefly by many threads and one held for a long time "
            + "by two are different problems that the totals alone cannot separate.")
    public String monitors() {
        List<ContentionStat> contention = profileManager.blockingManager().monitorContention();
        List<MonitorWaitStat> waits = profileManager.blockingManager().monitorWaits();
        if (contention.isEmpty() && waits.isEmpty()) {
            return NO_MONITOR_DATA;
        }

        return LinkedOutput.json(new Monitors(
                trimContention(contention),
                trimWaits(waits),
                NextSteps.builder().add(STEP_FRAMES).add(STEP_OVERVIEW).build(),
                UiLinks.view(profileId(), BLOCKING_VIEW)));
    }

    @Tool(description = "Virtual threads that pinned their carrier, with how long each pin lasted. "
            + "Pinning is the failure mode Loom introduces and no flamegraph shows it: while a virtual "
            + "thread is pinned, every other virtual thread scheduled on that carrier waits, and the "
            + "carrier looks merely busy.")
    public String pinnedThreads() {
        List<PinnedThreadEntry> pinned = profileManager.blockingManager().pinnedThreads();
        if (pinned.isEmpty()) {
            return NO_PINNED_DATA;
        }

        return LinkedOutput.json(new Pinned(
                trimPinned(pinned),
                NextSteps.builder().add(STEP_PIN_CAUSE).add(STEP_OVERVIEW).build(),
                UiLinks.view(profileId(), VIRTUAL_THREADS_VIEW)));
    }

    /**
     * Whether the recording carries any blocking event type at all - the flags rather than the counts,
     * so a recording that captured the events and saw nothing block is still reported as measured.
     */
    private static boolean nothingRecorded(BlockingOverview overview) {
        return !overview.hasMonitorEnter()
                && !overview.hasMonitorWaits()
                && !overview.hasParks()
                && !overview.hasSleeps()
                && !overview.hasPinned();
    }

    private static List<ContentionStat> trimContention(List<ContentionStat> stats) {
        return stats.size() <= MAX_ROWS ? stats : stats.subList(0, MAX_ROWS);
    }

    private static List<MonitorWaitStat> trimWaits(List<MonitorWaitStat> stats) {
        return stats.size() <= MAX_ROWS ? stats : stats.subList(0, MAX_ROWS);
    }

    private static List<PinnedThreadEntry> trimPinned(List<PinnedThreadEntry> entries) {
        return entries.size() <= MAX_ROWS ? entries : entries.subList(0, MAX_ROWS);
    }

    private String profileId() {
        return profileManager.info().id();
    }

    private record BlockingDashboard(
            BlockingOverview overview, List<String> nextSteps, String uiLink) {
    }

    private record Monitors(
            List<ContentionStat> contention,
            List<MonitorWaitStat> waits,
            List<String> nextSteps,
            String uiLink) {
    }

    private record Pinned(
            List<PinnedThreadEntry> pinnedThreads, List<String> nextSteps, String uiLink) {
    }
}
