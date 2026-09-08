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

import cafe.jeffrey.profile.manager.ContainerManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.common.event.ContainerConfiguration;
import cafe.jeffrey.profile.manager.model.container.ContainerCpuThrottlingData;
import cafe.jeffrey.profile.mcp.finding.McpFinding;
import cafe.jeffrey.shared.common.model.Type;

import java.util.List;
import java.util.Set;

/**
 * The cgroup limits the JVM started under, and whether the scheduler throttled it.
 * <p>
 * The throttling verdict is one of the two judgements the surface makes of its own accord — the
 * auto-analysis rules are the other — so it is reported as a finding in the shared shape, carrying
 * the counters it rests on, rather than as a verdict record of this section's own. A verdict the
 * data cannot support ({@code NOT_APPLICABLE}) is no finding at all, not a finding of no severity.
 */
public record ContainerSection(ProfileManager profileManager) implements JvmSection {

    public static final String ID = "container";
    private static final String TITLE = "Container";

    static final String SOURCE = "jvm_container";
    static final String FINDING_CATEGORY = "container";
    static final String FINDING_SUBJECT = "cpu-throttling";
    private static final String NEXT_TOOL = "jvm_threads";
    private static final String THROTTLED_ACTION =
            "Compare the CPU limit against the per-thread load in jvm_threads before raising the quota: "
                    + "a few hot threads and a low limit throttle the same way as a saturated process.";

    private static final String EVIDENCE_THROTTLED = "throttled";
    private static final String EVIDENCE_THROTTLED_PERIODS = "throttledPeriods";
    private static final String EVIDENCE_ELAPSED_PERIODS = "elapsedPeriods";
    private static final String EVIDENCE_THROTTLED_TIME_MILLIS = "throttledTimeMillis";
    private static final String EVIDENCE_OVERALL_RATIO_PCT = "overallRatioPct";
    private static final String EVIDENCE_PEAK_RATIO_PCT = "peakRatioPct";
    private static final String EVIDENCE_CPU_LIMIT_CORES = "cpuLimitCores";

    private static final Set<Type> EVENT_TYPES = Set.of(
            Type.CONTAINER_CONFIGURATION,
            Type.CONTAINER_CPU_THROTTLING);

    private static final List<String> NEXT_STEPS = List.of(
            "Throttling caps CPU without appearing in a flamegraph — frames simply stop being sampled. "
                    + "Compare these limits against the per-thread CPU load in jvm_threads.",
            "These are the limits the JVM read at start-up, which can differ from what the orchestrator's "
                    + "manifest says today.");

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
        ContainerManager manager = profileManager.containerManager();
        ContainerCpuThrottlingData throttling = manager.throttling();
        return new ContainerDashboard(
                manager.configuration().configuration(),
                findings(throttling),
                throttling.summary());
    }

    private static List<McpFinding> findings(ContainerCpuThrottlingData throttling) {
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
                .nextTool(NEXT_TOOL);
        if (summary != null) {
            finding.evidence(EVIDENCE_THROTTLED_PERIODS, summary.throttledPeriods())
                    .evidence(EVIDENCE_ELAPSED_PERIODS, summary.elapsedPeriods())
                    .evidence(EVIDENCE_THROTTLED_TIME_MILLIS, summary.throttledTimeMillis())
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
     * @param findings the throttling verdict as a finding, or empty when the recording cannot say
     */
    private record ContainerDashboard(
            ContainerConfiguration configuration,
            List<McpFinding> findings,
            ContainerCpuThrottlingData.Summary summary) {
    }
}
