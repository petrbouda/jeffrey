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
import cafe.jeffrey.profile.common.event.ContainerConfiguration;
import cafe.jeffrey.profile.manager.ContainerManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.model.container.ContainerCpuThrottlingData;
import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.microscope.core.mcp.tools.NextCalls;
import cafe.jeffrey.profile.mcp.finding.McpFinding;
import cafe.jeffrey.profile.mcp.finding.McpFindings;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The cgroup limits the JVM started under, and whether the scheduler throttled it.
 * <p>
 * The throttling verdict is one of the two judgements the surface makes of its own accord — the
 * auto-analysis rules are the other — so it is reported as a finding in the shared shape, carrying
 * the counters it rests on, rather than as a verdict record of this section's own. A verdict the
 * data cannot support ({@code NOT_APPLICABLE}) is no finding at all, not a finding of no severity.
 */
public record ContainerSection(ProfileManager profileManager) implements JvmSection<ContainerSection.ContainerDashboard> {

    public static final String ID = "container";
    private static final String TITLE = "Container";

    static final String SOURCE = "jvm_container";
    static final String FINDING_CATEGORY = "container";
    static final String FINDING_SUBJECT = "cpu-throttling";
    private static final String NEXT_TOOL = "jvm_threads";
    private static final String PROFILE_ID = "profileId";
    private static final String NEXT_TOOL_WHY = "shows the per-thread CPU load to set against the limit";
    private static final String THROTTLED_ACTION =
            "Compare the CPU limit against the per-thread load in jvm_threads before raising the quota: "
                    + "a few hot threads and a low limit throttle the same way as a saturated process.";

    private static final String EVIDENCE_THROTTLED = "throttled";
    private static final String EVIDENCE_THROTTLED_PERIODS = "throttledPeriods";
    private static final String EVIDENCE_ELAPSED_PERIODS = "elapsedPeriods";
    private static final String EVIDENCE_THROTTLED_TIME_MS = "throttledTimeMs";
    private static final String EVIDENCE_OVERALL_RATIO_PCT = "overallRatioPct";
    private static final String EVIDENCE_PEAK_RATIO_PCT = "peakRatioPct";
    private static final String EVIDENCE_CPU_LIMIT_CORES = "cpuLimitCores";

    private static final Set<Type> EVENT_TYPES = Set.of(
            Type.CONTAINER_CONFIGURATION,
            Type.CONTAINER_CPU_THROTTLING);

    private static final String LIMITS_GUIDANCE =
            "These are the limits the JVM read at start-up, which can differ from what the orchestrator's "
                    + "manifest says today.";
    private static final String THREADS_WHY =
            "shows the per-thread CPU load to set against these limits; throttling caps CPU without "
                    + "appearing in a flamegraph, whose frames simply stop being sampled";

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
        return MicroscopeView.CONTAINER_CONFIGURATION;
    }

    /**
     * The throttling page when there is a verdict to show on it, else the limits the JVM read.
     */
    @Override
    public MicroscopeView view(ContainerDashboard dashboard) {
        return dashboard.findings().isEmpty() ? view() : MicroscopeView.CONTAINER_CPU_THROTTLING;
    }

    @Override
    public void followUp(NextSteps.Builder next, ContainerDashboard dashboard) {
        next.next(NextCalls.to(NEXT_TOOL).with(PROFILE_ID, profileManager.info().id()).why(THREADS_WHY))
                .guidance(LIMITS_GUIDANCE);
    }

    @Override
    public ContainerDashboard reachable(ContainerDashboard dashboard, Predicate<String> servesTool) {
        return new ContainerDashboard(dashboard.configuration(),
                McpFindings.reachable(dashboard.findings(), servesTool), dashboard.summary());
    }

    @Override
    public ContainerDashboard render() {
        ContainerManager manager = profileManager.containerManager();
        ContainerCpuThrottlingData throttling = manager.throttling();
        return new ContainerDashboard(
                limits(manager.configuration().configuration()),
                findings(profileManager.info().id(), throttling),
                counters(throttling.summary()));
    }

    private static Limits limits(ContainerConfiguration configuration) {
        if (configuration == null) {
            return null;
        }
        return new Limits(
                configuration.containerType(),
                configuration.cpuSlicePeriod(),
                configuration.cpuQuota(),
                configuration.cpuShares(),
                configuration.effectiveCpuCount(),
                configuration.memorySoftLimit(),
                configuration.memoryLimit(),
                configuration.swapMemoryLimit(),
                configuration.hostTotalMemory(),
                configuration.hostTotalSwapMemory());
    }

    private static Throttling counters(ContainerCpuThrottlingData.Summary summary) {
        if (summary == null) {
            return null;
        }
        return new Throttling(
                summary.elapsedPeriods(),
                summary.throttledPeriods(),
                summary.throttledTimeMillis(),
                summary.overallRatioPct(),
                summary.peakRatioPct(),
                summary.cpuLimitCores(),
                summary.cfsPeriodMillis(),
                summary.effectiveCpuCount());
    }

    /**
     * The throttling verdict alone, as the finding {@link #render} carries: what a reader merging the
     * profile's judgements needs without the configuration and the counters around it. Reads the
     * stored throttling events; empty when the recording cannot support a verdict.
     */
    public List<McpFinding> findings() {
        return findings(profileManager.info().id(), profileManager.containerManager().throttling());
    }

    private static List<McpFinding> findings(String profileId, ContainerCpuThrottlingData throttling) {
        ContainerCpuThrottlingData.Verdict verdict = throttling.verdict();
        if (verdict == null || verdict.severity() == ContainerCpuThrottlingData.Severity.NOT_APPLICABLE) {
            return List.of();
        }
        ContainerCpuThrottlingData.Summary summary = throttling.summary();
        McpFinding.Builder finding = McpFinding.of(FINDING_CATEGORY, FINDING_SUBJECT)
                .severity(severity(verdict.severity()))
                .title(verdict.title())
                .detail(verdict.description())
                .source(SOURCE)
                .evidence(EVIDENCE_THROTTLED, verdict.throttled())
                .nextTool(NextCalls.to(NEXT_TOOL).with(PROFILE_ID, profileId).why(NEXT_TOOL_WHY));
        if (summary != null) {
            finding.evidence(EVIDENCE_THROTTLED_PERIODS, summary.throttledPeriods())
                    .evidence(EVIDENCE_ELAPSED_PERIODS, summary.elapsedPeriods())
                    .evidence(EVIDENCE_THROTTLED_TIME_MS, summary.throttledTimeMillis())
                    .evidence(EVIDENCE_OVERALL_RATIO_PCT, summary.overallRatioPct())
                    .evidence(EVIDENCE_PEAK_RATIO_PCT, summary.peakRatioPct())
                    .evidence(EVIDENCE_CPU_LIMIT_CORES, summary.cpuLimitCores());
        }
        if (verdict.throttled()) {
            finding.action(THROTTLED_ACTION);
        }
        return List.of(finding.build());
    }

    private static McpFinding.Severity severity(ContainerCpuThrottlingData.Severity severity) {
        return switch (severity) {
            case HIGH -> McpFinding.Severity.CRITICAL;
            case MEDIUM -> McpFinding.Severity.WARNING;
            case LOW -> McpFinding.Severity.INFO;
            case NONE -> McpFinding.Severity.OK;
            case NOT_APPLICABLE -> throw new IllegalArgumentException(
                    "A verdict the data cannot support is not a finding: severity=" + severity);
        };
    }

    /**
     * @param configuration the cgroup limits the JVM read at start-up; null when the recording has none
     * @param findings      the throttling verdict as a finding, or empty when the recording cannot say
     * @param summary       the throttling counters the verdict rests on; null without throttling events
     */
    public record ContainerDashboard(
            @McpNullable
            Limits configuration,
            List<McpFinding> findings,
            @McpNullable
            Throttling summary) {
    }

    /**
     * The limits as {@code jdk.ContainerConfiguration} reports them, each null when the recording does
     * not carry it. The CPU period and quota are {@code @Timespan} fields, which the recording stores in
     * nanoseconds; the throttling summary's {@code cfsPeriodMillis} is the same period in milliseconds.
     */
    public record Limits(
            @McpNullable
            String containerType,
            @McpNullable
            Long cpuSlicePeriodNanos,
            @McpNullable
            Long cpuQuotaNanos,
            @McpNullable
            Long cpuShares,
            @McpNullable
            Long effectiveCpuCount,
            @McpNullable
            Long memorySoftLimitBytes,
            @McpNullable
            Long memoryLimitBytes,
            @McpNullable
            Long swapMemoryLimitBytes,
            @McpNullable
            Long hostTotalMemoryBytes,
            @McpNullable
            Long hostTotalSwapMemoryBytes) {
    }

    /**
     * The scheduler's throttling counters over the recording.
     *
     * @param cpuLimitCores the quota as cores; null when the container sets no CPU quota
     */
    public record Throttling(
            long elapsedPeriods,
            long throttledPeriods,
            double throttledTimeMs,
            double overallRatioPct,
            double peakRatioPct,
            @McpNullable
            Double cpuLimitCores,
            @McpNullable
            Double cfsPeriodMs,
            @McpNullable
            Long effectiveCpuCount) {
    }

    /**
     * What {@code jvm_container} answers: the envelope every section shares, around this section's dashboard.
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
            ContainerDashboard dashboard,
            McpFollowUp followUp,
            @McpDescription(SectionHeader.UI_LINK)
            String uiLink) {

        public static Answer of(SectionHeader header, ContainerDashboard dashboard) {
            return new Answer(header.status(), header.reason(), header.profileId(), header.section(),
                    header.title(), dashboard, header.followUp(), header.uiLink());
        }
    }
}
