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

package cafe.jeffrey.microscope.core.mcp.tools.jvm;

import cafe.jeffrey.microscope.core.mcp.MicroscopeView;
import cafe.jeffrey.microscope.core.mcp.tools.NextSteps;
import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.model.thread.ThreadCpuLoads;
import cafe.jeffrey.profile.manager.model.thread.ThreadStats;
import cafe.jeffrey.profile.manager.model.thread.ThreadWithCpuLoad;
import cafe.jeffrey.profile.manager.model.virtualthread.VirtualThreadData;
import cafe.jeffrey.profile.manager.thread.ThreadManager;
import cafe.jeffrey.profile.manager.thread.VirtualThreadManager;
import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.provider.profile.api.AllocatingThread;

import java.util.List;
import java.util.Set;

/**
 * The Threads dashboard: how many there were, which of them burned the CPU and allocated the memory,
 * and — for a Loom application — where a carrier thread was pinned.
 * <p>
 * A flamegraph aggregates across threads, which is what makes it readable and also what hides the
 * answer to "which pool is doing this". Per-thread attribution is a different question and needs the
 * per-thread events: {@code jdk.ThreadCPULoad} for the CPU and
 * {@code jdk.ThreadAllocationStatistics} for the bytes.
 * <p>
 * Pinning is the Loom-specific failure worth naming on its own. A virtual thread pinned inside a
 * synchronized block or a native frame blocks its carrier, so the pool stops scaling for reasons no
 * amount of reading the application's own code makes obvious — and the reason field says which case
 * it was.
 */
public record ThreadsSection(ProfileManager profileManager) implements JvmSection<ThreadsSection.ThreadsDashboard> {

    public static final String ID = "threads";

    private static final String TITLE = "Threads";

    /** Threads carried back per ranking — the CPU consumers, the allocators, the pinned carriers. */
    private static final int THREADS_LIMIT = 15;

    private static final double NANOS_IN_MILLI = 1_000_000d;

    private static final Set<Type> EVENT_TYPES = Set.of(
            Type.JAVA_THREAD_STATISTICS,
            Type.THREAD_CPU_LOAD,
            Type.THREAD_ALLOCATION_STATISTICS,
            Type.VIRTUAL_THREAD_START,
            Type.VIRTUAL_THREAD_PINNED);

    private static final String ONE_THREAD_WHY =
            "splits the CPU flamegraph by thread, the frames this per-thread attribution cannot show";
    private static final String ALLOCATION_WHY =
            "says where the allocating threads allocate; this dashboard says only who";
    private static final String PINNING_GUIDANCE =
            "A pinning reason names a synchronized block or a native call. Read that code before proposing "
                    + "the fix.";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return TITLE;
    }

    @Override
    public Set<Type> eventTypes() {
        return EVENT_TYPES;
    }

    @Override
    public MicroscopeView view() {
        return MicroscopeView.THREAD_STATISTICS;
    }

    @Override
    public void followUp(NextSteps.Builder next, ThreadsDashboard dashboard) {
        String profileId = profileManager.info().id();
        SectionCalls.onCpu(next, profileManager, eventType -> SectionCalls.on(SectionCalls.FLAMEGRAPH_EXPORT, profileId)
                .with(SectionCalls.EVENT_TYPE, eventType)
                .with(SectionCalls.THREAD_MODE, true)
                .why(ONE_THREAD_WHY));
        if (!dashboard.topAllocating().isEmpty()) {
            SectionCalls.allocationPaths(next, profileManager, ALLOCATION_WHY);
        }
        next.guidanceWhen(dashboard.virtualThreads() != null && dashboard.virtualThreads().pinningCount() > 0,
                        PINNING_GUIDANCE);
    }

    @Override
    public ThreadsDashboard render() {
        ThreadManager threadManager = profileManager.threadManager();
        ThreadStats stats = threadManager.threadStatistics();
        ThreadCpuLoads cpuLoads = threadManager.threadCpuLoads(THREADS_LIMIT);

        return new ThreadsDashboard(
                new Population(stats.accumulated(), stats.peak()),
                new BlockingCounts(stats.sleepCount(), stats.parkCount(), stats.monitorBlockCount()),
                cpuLoad(cpuLoads.user()),
                cpuLoad(cpuLoads.system()),
                allocating(threadManager.threadsAllocatingMemory(THREADS_LIMIT)),
                threadManager.resolveAllocationType().code(),
                virtualThreads(profileManager.virtualThreadManager()));
    }

    private static List<CpuThread> cpuLoad(List<ThreadWithCpuLoad> loads) {
        return loads.stream()
                .map(load -> new CpuThread(load.threadInfo().name(), load.cpuLoad() == null ? null : load.cpuLoad().doubleValue()))
                .toList();
    }

    private static List<AllocatingThreadRow> allocating(List<AllocatingThread> threads) {
        return threads.stream()
                .map(thread -> new AllocatingThreadRow(
                        thread.threadInfo().name(), thread.allocatedBytes()))
                .toList();
    }

    private static VirtualThreads virtualThreads(VirtualThreadManager manager) {
        VirtualThreadData data = manager.virtualThreadData();
        VirtualThreadData.VtHeader header = data.header();
        if (header == null) {
            return null;
        }

        List<PinnedThread> pinnedThreads = data.topPinnedThreads().stream()
                .limit(THREADS_LIMIT)
                .map(pinned -> new PinnedThread(
                        pinned.threadName(),
                        pinned.count(),
                        millis(pinned.totalNanos()),
                        millis(pinned.maxNanos())))
                .toList();

        List<PinningReason> reasons = data.pinningReasons().stream()
                .map(reason -> new PinningReason(
                        reason.reason(),
                        reason.count(),
                        millis(reason.totalNanos()),
                        millis(reason.maxNanos())))
                .toList();

        return new VirtualThreads(
                header.startedCount(),
                header.endedCount(),
                header.peakLiveCount(),
                header.pinningCount(),
                millis(header.totalPinnedNanos()),
                millis(header.maxPinnedNanos()),
                header.submitFailedCount(),
                pinnedThreads,
                reasons);
    }

    private static double millis(long nanos) {
        return nanos / NANOS_IN_MILLI;
    }

    /**
     * @param allocationEventType the event type the allocation ranking was attributed from, because a
     *                            recording carrying only the TLAB events is sampled differently from
     *                            one carrying {@code jdk.ThreadAllocationStatistics}
     * @param virtualThreads      null when the recording carries no virtual-thread events
     */
    public record ThreadsDashboard(
            Population population,
            BlockingCounts blocking,
            @McpDescription("The " + THREADS_LIMIT + " threads with the most user CPU")
            List<CpuThread> topUserCpu,
            @McpDescription("The " + THREADS_LIMIT + " threads with the most system CPU")
            List<CpuThread> topSystemCpu,
            @McpDescription("The " + THREADS_LIMIT + " threads that allocated the most bytes")
            List<AllocatingThreadRow> topAllocating,
            @McpNullable
            String allocationEventType,
            @McpNullable
            VirtualThreads virtualThreads) {
    }

    /**
     * @param accumulated every thread the recording ever saw, started and finished alike
     * @param peak        the most that were alive at once
     */
    public record Population(long accumulated, long peak) {
    }

    public record BlockingCounts(long sleeps, long parks, long monitorBlocks) {
    }

    public record CpuThread(
            String threadName,
            @McpNullable
            Double cpuLoad) {
    }

    public record AllocatingThreadRow(String threadName, long allocatedBytes) {
    }

    /**
     * @param pinningCount  how often a virtual thread pinned its carrier — the number that decides
     *                      whether Loom is scaling here at all
     * @param reasons       why the carrier was pinned, which is what says whether the fix is a
     *                      synchronized block or a native call
     */
    public record VirtualThreads(
            long startedCount,
            long endedCount,
            long peakLiveCount,
            long pinningCount,
            double totalPinnedMs,
            double maxPinnedMs,
            long submitFailedCount,
            @McpDescription("The " + THREADS_LIMIT + " virtual threads pinned most often")
            List<PinnedThread> topPinnedThreads,
            List<PinningReason> reasons) {
    }

    public record PinnedThread(
            String threadName, long count, double totalMs, double maxMs) {
    }

    public record PinningReason(
            @McpNullable
            String reason,
            long count,
            double totalMs,
            double maxMs) {
    }

    /**
     * What {@code jvm_threads} answers: the envelope every section shares, around this section's dashboard.
     */
    public record Answer(
            SectionStatus status,
            @McpNullable
            @McpDescription(SectionHeader.REASON)
            String reason,
            String profileId,
            @McpDescription(SectionHeader.SECTION)
            String section,
            String title,
            @McpNullable
            @McpDescription(SectionHeader.DASHBOARD)
            ThreadsDashboard dashboard,
            McpFollowUp followUp,
            @McpDescription(SectionHeader.UI_LINK)
            String uiLink) {

        public static Answer of(SectionHeader header, ThreadsDashboard dashboard) {
            return new Answer(header.status(), header.reason(), header.profileId(), header.section(),
                    header.title(), dashboard, header.followUp(), header.uiLink());
        }
    }
}
