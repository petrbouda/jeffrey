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
import cafe.jeffrey.profile.manager.VmOperationManager;
import cafe.jeffrey.profile.manager.model.vmoperation.SafepointLatencyData;
import cafe.jeffrey.profile.manager.model.vmoperation.SafepointOffender;
import cafe.jeffrey.profile.manager.model.vmoperation.VmOperationStat;
import cafe.jeffrey.profile.manager.model.vmoperation.VmOverview;
import cafe.jeffrey.shared.common.model.Type;

import java.util.List;
import java.util.Set;

/**
 * The Safepoints and VM Operations dashboard — the pauses that are not garbage collection.
 * <p>
 * This is the section that answers "GC looks fine and we still have pauses". Every VM operation runs
 * the application to a stop the same way a collection does, and a thread that is slow to reach the
 * safepoint holds every other thread there while it finishes: the pause a user feels is
 * time-to-safepoint plus the operation, not the operation alone.
 * <p>
 * The offenders are the part worth having a tool for. {@code jdk.SafepointLatency} fires once per
 * thread per safepoint, so a recording with a few hundred safepoints and a few hundred threads holds
 * tens of thousands of events that individually say nothing; the question — which thread is habitually
 * slow to yield — is about their distribution, which is what {@link SafepointLatencyData} already
 * computes. {@code threadState} is what turns the number into a diagnosis: slow from
 * {@code _thread_in_Java} is a loop the JIT stripped the safepoint poll out of, slow from
 * {@code _thread_in_native} is a call the JVM cannot interrupt at all.
 */
public record SafepointsSection(ProfileManager profileManager) implements JvmSection {

    public static final String ID = "safepoints";

    private static final String TITLE = "Safepoints and VM Operations";

    /** Threads named as habitual offenders. Beyond this the tail stops being diagnostic. */
    private static final int OFFENDERS_LIMIT = 15;

    private static final double NANOS_IN_MILLI = 1_000_000d;

    private static final Set<Type> EVENT_TYPES = Set.of(
            Type.EXECUTE_VM_OPERATION,
            Type.SAFEPOINT_STATE_SYNCHRONIZATION,
            Type.SAFEPOINT_LATENCY);

    private static final List<String> NEXT_STEPS = List.of(
            "The collector's own pauses are in jvm_gc; this section is everything else that stops the "
                    + "application.",
            "A thread slow from _thread_in_Java is a loop the JIT stripped the safepoint poll out of; one "
                    + "slow from _thread_in_native is inside a call the JVM cannot interrupt. Read the method "
                    + "in the checkout before concluding which.");

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
    public List<String> nextSteps() {
        return NEXT_STEPS;
    }

    @Override
    public Object render() {
        VmOperationManager manager = profileManager.vmOperationManager();
        VmOverview overview = manager.overview();
        SafepointLatencyData latency = manager.safepointOffenders();

        return new SafepointsDashboard(
                overview.vmOperationCount(),
                millis(overview.totalSafepointPauseNanos()),
                millis(overview.longestPauseNanos()),
                overview.longestPauseOperation(),
                operations(manager.vmOperations()),
                timeToSafepoint(latency),
                offenders(latency.offenders()));
    }

    private static List<Operation> operations(List<VmOperationStat> stats) {
        return stats.stream()
                .map(stat -> new Operation(
                        stat.operation(),
                        stat.count(),
                        millis(stat.totalNanos()),
                        millis(stat.maxNanos()),
                        stat.safepoint(),
                        stat.blocking()))
                .toList();
    }

    private static TimeToSafepoint timeToSafepoint(SafepointLatencyData latency) {
        return new TimeToSafepoint(
                latency.threadCount(),
                millis(latency.totalNanos()),
                millis(latency.worstNanos()));
    }

    private static List<Offender> offenders(List<SafepointOffender> offenders) {
        return offenders.stream()
                .limit(OFFENDERS_LIMIT)
                .map(offender -> new Offender(
                        offender.threadName(),
                        offender.threadState(),
                        offender.count(),
                        millis(offender.totalNanos()),
                        millis(offender.p99Nanos()),
                        millis(offender.maxNanos())))
                .toList();
    }

    private static double millis(long nanos) {
        return nanos / NANOS_IN_MILLI;
    }

    /**
     * @param totalSafepointPauseMillis the application's whole stop-the-world budget outside the
     *                                  collector, which is the figure to compare against the GC one
     * @param timeToSafepoint           how long threads took to reach the safepoints, in aggregate
     * @param offenders                 the threads that kept everyone else waiting, worst first
     */
    private record SafepointsDashboard(
            long vmOperationCount,
            double totalSafepointPauseMillis,
            double longestPauseMillis,
            String longestPauseOperation,
            List<Operation> operations,
            TimeToSafepoint timeToSafepoint,
            List<Offender> offenders) {
    }

    private record Operation(
            String operation,
            long count,
            double totalMillis,
            double maxMillis,
            boolean safepoint,
            boolean blocking) {
    }

    private record TimeToSafepoint(int measuredThreads, double totalMillis, double worstMillis) {
    }

    private record Offender(
            String threadName,
            String threadState,
            long safepoints,
            double totalMillis,
            double p99Millis,
            double maxMillis) {
    }
}
