/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies;
import cafe.jeffrey.microscope.core.mcp.MicroscopeView;
import cafe.jeffrey.microscope.core.mcp.UiLinks;
import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;
import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.profile.manager.BlockingManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.model.blocking.BlockingOverview;
import cafe.jeffrey.profile.manager.model.blocking.ContentionStat;
import cafe.jeffrey.profile.manager.model.blocking.MonitorWaitStat;
import cafe.jeffrey.profile.manager.model.blocking.PinnedThreadEntry;
import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.profile.mcp.McpNextTool;
import cafe.jeffrey.profile.mcp.McpToolCost;
import cafe.jeffrey.profile.mcp.McpToolMeta;
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

    private static final MicroscopeView BLOCKING_VIEW = MicroscopeView.BLOCKING_OPERATIONS;
    private static final MicroscopeView VIRTUAL_THREADS_VIEW = MicroscopeView.VIRTUAL_THREADS;

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

    private static final String MONITORS_WHY =
            "aggregates per monitor class, which names the lock rather than the call site that hit it";
    private static final String FRAMES_WHY =
            "the call paths that reached a lock, weighed by the nanoseconds blocked";
    private static final String PINNED_WHY =
            "the pinned virtual threads and for how long; a pinned carrier blocks every virtual thread on it";
    private static final String OVERVIEW_WHY =
            "these figures beside the waits, parks and sleeps, and which blocking event types were recorded";
    private static final String PIN_CAUSE =
            "A pin comes from a synchronized block or a native call on the carrier. Read that code "
                    + "before proposing the fix; the profile names the thread, not the reason.";

    private final ProfileManager profileManager;
    private final AdvertisedFamilies advertised;

    public BlockingMcpTools(ProfileManager profileManager, AdvertisedFamilies advertised) {
        this.profileManager = profileManager;
        this.advertised = advertised;
    }

    @Tool(description = "Reports where threads waited instead of running: contended monitors and the "
            + "total time blocked on them in nanoseconds, how many waits, parks and sleeps there were, "
            + "and how often a virtual thread pinned its carrier. Answers 'the CPU is idle and it is "
            + "still slow'. Each figure comes with whether its event type was recorded at all, so an "
            + "absent one is not read as a zero. status NOT_RECORDED: no blocking event type was "
            + "recorded.")
    @McpOutputSchema(BlockingDashboard.class)
    @McpToolMeta(cost = McpToolCost.MODERATE)
    public McpToolResult overview() {
        String uiLink = UiLinks.view(profileId(), BLOCKING_VIEW);
        BlockingOverview overview = profileManager.blockingManager().overview();
        if (nothingRecorded(overview)) {
            return McpToolResult.of(new BlockingDashboard(DashboardStatus.NOT_RECORDED, NO_BLOCKING_DATA,
                    profileId(), null, NextSteps.builder(advertised).followUp(), uiLink));
        }

        McpFollowUp followUp = NextSteps.builder(advertised)
                .nextWhen(overview.hasMonitorEnter() || overview.hasMonitorWaits(),
                        call(FollowUpCalls.BLOCKING_MONITORS).why(MONITORS_WHY))
                .nextWhen(overview.pinnedCount() > 0, call(FollowUpCalls.BLOCKING_PINNED).why(PINNED_WHY))
                .nextWhen(overview.hasMonitorEnter(), monitorFrames())
                .followUp();
        return McpToolResult.of(new BlockingDashboard(DashboardStatus.OK, null, profileId(), overview, followUp,
                uiLink));
    }

    @Tool(description = "Aggregates monitor contention per lock: the class of each monitor, how many "
            + "times threads blocked on it, for how long in total and at worst (nanoseconds), and how "
            + "many distinct threads were involved. A lock held briefly by many threads and one held "
            + "for a long time by two are different problems that the totals alone cannot separate. "
            + "Each list keeps its 40 costliest monitors and counts the rest. status NOT_RECORDED: "
            + "neither monitor event type was recorded.")
    @McpOutputSchema(Monitors.class)
    @McpToolMeta(cost = McpToolCost.MODERATE)
    public McpToolResult monitors() {
        String uiLink = UiLinks.view(profileId(), BLOCKING_VIEW);
        List<ContentionStat> contention = profileManager.blockingManager().monitorContention();
        List<MonitorWaitStat> waits = profileManager.blockingManager().monitorWaits();
        if (contention.isEmpty() && waits.isEmpty()) {
            return McpToolResult.of(new Monitors(DashboardStatus.NOT_RECORDED, NO_MONITOR_DATA, profileId(),
                    List.of(), null, List.of(), null, overviewOnly(), uiLink));
        }

        List<ContentionStat> contended = ToolArguments.firstOf(contention, MAX_ROWS);
        List<MonitorWaitStat> waited = ToolArguments.firstOf(waits, MAX_ROWS);
        McpFollowUp followUp = NextSteps.builder(advertised)
                .nextWhen(!contention.isEmpty(), monitorFrames())
                .next(call(FollowUpCalls.BLOCKING_OVERVIEW).why(OVERVIEW_WHY))
                .followUp();
        return McpToolResult.of(new Monitors(DashboardStatus.OK, null, profileId(),
                contended.stream().map(Contention::of).toList(), contention.size() - contended.size(),
                waited.stream().map(MonitorWait::of).toList(), waits.size() - waited.size(),
                followUp, uiLink));
    }

    @Tool(description = "Returns the virtual threads that pinned their carrier, with how long each pin "
            + "lasted in nanoseconds, the longest first. Pinning is the failure mode Loom introduces and "
            + "no flamegraph shows it: while a virtual thread is pinned, every other virtual thread "
            + "scheduled on that carrier waits, and the carrier looks merely busy. The 40 longest "
            + "pins are kept; omittedPinnedThreads counts the rest, or is null when more may exist "
            + "than the profile keeps. status NOT_RECORDED: no pin was recorded.")
    @McpOutputSchema(Pinned.class)
    @McpToolMeta(cost = McpToolCost.MODERATE)
    public McpToolResult pinnedThreads() {
        String uiLink = UiLinks.view(profileId(), VIRTUAL_THREADS_VIEW);
        List<PinnedThreadEntry> pinned = profileManager.blockingManager().pinnedThreads();
        if (pinned.isEmpty()) {
            return McpToolResult.of(new Pinned(DashboardStatus.NOT_RECORDED, NO_PINNED_DATA, profileId(),
                    List.of(), null, overviewOnly(), uiLink));
        }

        List<PinnedThreadEntry> shown = ToolArguments.firstOf(pinned, MAX_ROWS);
        McpFollowUp followUp = NextSteps.builder(advertised)
                .next(call(FollowUpCalls.BLOCKING_OVERVIEW).why(OVERVIEW_WHY))
                .guidance(PIN_CAUSE)
                .followUp();
        return McpToolResult.of(new Pinned(DashboardStatus.OK, null, profileId(),
                shown.stream().map(Pin::of).toList(), omittedPins(pinned.size(), shown.size()), followUp, uiLink));
    }

    /**
     * The pins this answer left out, counted only while the manager's list is shorter than what it
     * keeps: a list at that cap may have had more behind it, which nothing here can count.
     */
    private static Integer omittedPins(int kept, int shown) {
        return kept >= BlockingManager.PINNED_THREADS_KEPT ? null : kept - shown;
    }

    private McpFollowUp overviewOnly() {
        return NextSteps.builder(advertised)
                .next(call(FollowUpCalls.BLOCKING_OVERVIEW).why(OVERVIEW_WHY))
                .followUp();
    }

    private McpNextTool monitorFrames() {
        return call(FollowUpCalls.FLAMEGRAPH_EXPORT)
                .with(FollowUpCalls.EVENT_TYPE, FollowUpCalls.MONITOR_ENTER_EVENT)
                .with(FollowUpCalls.USE_WEIGHT, true)
                .why(FRAMES_WHY);
    }

    private McpNextTool.Call call(String tool) {
        return NextCalls.to(tool).with(FollowUpCalls.PROFILE_ID, profileId());
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

    private String profileId() {
        return profileManager.info().id();
    }

    /** One contended monitor class; the builder files one its events did not name under an unknown label. */
    record Contention(
            String className,
            long count,
            long totalNanos,
            long maxNanos,
            int threadCount) {

        static Contention of(ContentionStat stat) {
            return new Contention(stat.className(), stat.count(), stat.totalNanos(), stat.maxNanos(),
                    stat.threadCount());
        }
    }

    /** One monitor class waited on; the builder files one its events did not name under an unknown label. */
    record MonitorWait(
            String className,
            long count,
            long totalNanos,
            long maxNanos,
            int threadCount,
            long timedOutCount) {

        static MonitorWait of(MonitorWaitStat stat) {
            return new MonitorWait(stat.className(), stat.count(), stat.totalNanos(), stat.maxNanos(),
                    stat.threadCount(), stat.timedOutCount());
        }
    }

    /** One pin; the thread is null when its event did not name it. */
    record Pin(
            @McpNullable
            String thread,
            long durationNanos) {

        static Pin of(PinnedThreadEntry entry) {
            return new Pin(entry.thread(), entry.durationNanos());
        }
    }

    record BlockingDashboard(
            DashboardStatus status,
            @McpNullable
            @McpDescription("Why there is no dashboard; null when status is OK")
            String reason,
            String profileId,
            @McpNullable
            @McpDescription("The headline counts and which event types were recorded; null when status is NOT_RECORDED")
            BlockingOverview overview,
            McpFollowUp followUp,
            @McpDescription("The blocking-operations page in the Microscope UI, for the user")
            String uiLink) {
    }

    record Monitors(
            DashboardStatus status,
            @McpNullable
            @McpDescription("Why there are no monitors; null when status is OK")
            String reason,
            String profileId,
            @McpDescription("Contended monitor enters by class, the costliest first, at most 40")
            List<Contention> contention,
            @McpNullable
            @McpDescription("Classes left out of contention by its cap of 40; null when status is NOT_RECORDED")
            Integer omittedContention,
            @McpDescription("Monitor waits by class, at most 40")
            List<MonitorWait> waits,
            @McpNullable
            @McpDescription("Classes left out of waits by its cap of 40; null when status is NOT_RECORDED")
            Integer omittedWaits,
            McpFollowUp followUp,
            @McpDescription("The blocking-operations page in the Microscope UI, for the user")
            String uiLink) {
    }

    record Pinned(
            DashboardStatus status,
            @McpNullable
            @McpDescription("Why there are no pins; null when status is OK")
            String reason,
            String profileId,
            @McpDescription("The longest pins, longest first, at most 40")
            List<Pin> pinnedThreads,
            @McpNullable
            @McpDescription("Pins left out of pinnedThreads; null when not recorded, or when the profile kept "
                    + "as many as it keeps and more may exist")
            Integer omittedPinnedThreads,
            McpFollowUp followUp,
            @McpDescription("The virtual-threads page in the Microscope UI, for the user")
            String uiLink) {
    }
}
