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
import cafe.jeffrey.profile.manager.VmOperationManager;
import cafe.jeffrey.profile.manager.model.vmoperation.SafepointLatencyData;
import cafe.jeffrey.profile.manager.model.vmoperation.SafepointOffender;
import cafe.jeffrey.profile.manager.model.vmoperation.VmOperationStat;
import cafe.jeffrey.profile.manager.model.vmoperation.VmOverview;
import cafe.jeffrey.profile.mcp.McpFollowUp;

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
public record SafepointsSection(ProfileManager profileManager) implements JvmSection<SafepointsSection.SafepointsDashboard> {

    public static final String ID = "safepoints";

    private static final String TITLE = "Safepoints and VM Operations";

    /** Threads named as habitual offenders. Beyond this the tail stops being diagnostic. */
    private static final int OFFENDERS_LIMIT = 15;

    private static final double NANOS_IN_MILLI = 1_000_000d;

    private static final Set<Type> EVENT_TYPES = Set.of(
            Type.EXECUTE_VM_OPERATION,
            Type.SAFEPOINT_STATE_SYNCHRONIZATION,
            Type.SAFEPOINT_LATENCY);

    private static final String GC_WHY =
            "shows the collector's own pauses; this section is everything else that stops the application";
    private static final String THREAD_STATE_GUIDANCE =
            "A thread slow from _thread_in_Java is a loop the JIT stripped the safepoint poll out of; one "
                    + "slow from _thread_in_native is inside a call the JVM cannot interrupt. Read the method "
                    + "in the checkout before concluding which.";

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
        return MicroscopeView.VM_OPERATIONS;
    }

    @Override
    public void followUp(NextSteps.Builder next, SafepointsDashboard dashboard) {
        next.next(SectionCalls.on(SectionCalls.JVM_GC, profileManager.info().id()).why(GC_WHY))
                .guidanceWhen(!dashboard.offenders().isEmpty(), THREAD_STATE_GUIDANCE);
    }

    @Override
    public SafepointsDashboard render() {
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
     * @param totalSafepointPauseMs the application's whole stop-the-world budget outside the
     *                                  collector, which is the figure to compare against the GC one
     * @param timeToSafepoint           how long threads took to reach the safepoints, in aggregate
     * @param offenders                 the threads that kept everyone else waiting, worst first
     */
    public record SafepointsDashboard(
            long vmOperationCount,
            double totalSafepointPauseMs,
            double longestPauseMs,
            @McpNullable
            String longestPauseOperation,
            List<Operation> operations,
            TimeToSafepoint timeToSafepoint,
            @McpDescription("The " + OFFENDERS_LIMIT + " threads that kept everyone else waiting longest, worst first")
            List<Offender> offenders) {
    }

    public record Operation(
            String operation,
            long count,
            double totalMs,
            double maxMs,
            boolean safepoint,
            boolean blocking) {
    }

    public record TimeToSafepoint(int measuredThreads, double totalMs, double worstMs) {
    }

    public record Offender(
            String threadName,
            @McpNullable
            String threadState,
            long safepoints,
            double totalMs,
            double p99Ms,
            double maxMs) {
    }

    /**
     * What {@code jvm_safepoints} answers: the envelope every section shares, around this section's dashboard.
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
            SafepointsDashboard dashboard,
            McpFollowUp followUp,
            @McpDescription(SectionHeader.UI_LINK)
            String uiLink) {

        public static Answer of(SectionHeader header, SafepointsDashboard dashboard) {
            return new Answer(header.status(), header.reason(), header.profileId(), header.section(),
                    header.title(), dashboard, header.followUp(), header.uiLink());
        }
    }
}
