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

package cafe.jeffrey.microscope.core.mcp.tools.jvm;

import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.model.thread.ThreadCpuLoads;
import cafe.jeffrey.profile.manager.model.thread.ThreadStats;
import cafe.jeffrey.profile.manager.model.thread.ThreadWithCpuLoad;
import cafe.jeffrey.profile.manager.model.virtualthread.VirtualThreadData;
import cafe.jeffrey.profile.manager.thread.ThreadManager;
import cafe.jeffrey.profile.manager.thread.VirtualThreadManager;
import cafe.jeffrey.provider.profile.api.AllocatingThread;
import cafe.jeffrey.shared.common.model.Type;

import java.math.BigDecimal;
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
public record ThreadsSection(ProfileManager profileManager) implements JvmSection {

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
    public Object render() {
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
                .map(load -> new CpuThread(load.threadInfo().name(), load.cpuLoad()))
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
    private record ThreadsDashboard(
            Population population,
            BlockingCounts blocking,
            List<CpuThread> topUserCpu,
            List<CpuThread> topSystemCpu,
            List<AllocatingThreadRow> topAllocating,
            String allocationEventType,
            VirtualThreads virtualThreads) {
    }

    /**
     * @param accumulated every thread the recording ever saw, started and finished alike
     * @param peak        the most that were alive at once
     */
    private record Population(long accumulated, long peak) {
    }

    private record BlockingCounts(long sleeps, long parks, long monitorBlocks) {
    }

    private record CpuThread(String threadName, BigDecimal cpuLoad) {
    }

    private record AllocatingThreadRow(String threadName, long allocatedBytes) {
    }

    /**
     * @param pinningCount  how often a virtual thread pinned its carrier — the number that decides
     *                      whether Loom is scaling here at all
     * @param reasons       why the carrier was pinned, which is what says whether the fix is a
     *                      synchronized block or a native call
     */
    private record VirtualThreads(
            long startedCount,
            long endedCount,
            long peakLiveCount,
            long pinningCount,
            double totalPinnedMillis,
            double maxPinnedMillis,
            long submitFailedCount,
            List<PinnedThread> topPinnedThreads,
            List<PinningReason> reasons) {
    }

    private record PinnedThread(
            String threadName, long count, double totalMillis, double maxMillis) {
    }

    private record PinningReason(
            String reason, long count, double totalMillis, double maxMillis) {
    }
}
