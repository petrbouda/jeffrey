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
import cafe.jeffrey.microscope.core.mcp.tools.AutoAnalysisStatus;
import cafe.jeffrey.microscope.core.mcp.tools.McpOperationRegistry;
import cafe.jeffrey.microscope.core.mcp.tools.NextSteps;
import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.profile.common.analysis.AutoAnalysisResult;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.profile.mcp.finding.McpFinding;
import cafe.jeffrey.profile.mcp.finding.McpFindings;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The JMC rule set over the whole recording, as findings in the shared shape.
 * <p>
 * The rules are the one place in the surface that makes a judgement rather than reporting a figure,
 * which is why their output is the shared finding record rather than a dashboard of its own: a
 * reader merges it with what the dashboards say, by id, instead of re-parsing prose. A rule that
 * could not run on this recording is listed apart from the findings, because "did not run" and
 * "passed" must never read the same.
 * <p>
 * Whether the rules have run is asked of the manager ({@link AutoAnalysisStatus}), never inferred from
 * an empty result: a run that flagged nothing caches an empty list, and that is an answer.
 */
public record AutoAnalysisSection(ProfileManager profileManager)
        implements JvmSection<AutoAnalysisSection.AutoAnalysisDashboard> {

    public static final String ID = "autoAnalysis";
    private static final String TITLE = "Auto Analysis";

    private static final Set<Type> EVENT_TYPES = Set.of();

    private static final String FINDINGS_GUIDANCE =
            "Each finding names its category and a nextTool: follow it there for the figures rather than "
                    + "repeating the rule's action as a conclusion. A rule applies fixed thresholds that know "
                    + "nothing about this service's normal behaviour, so a fired rule is a lead.";
    private static final String NOT_EVALUATED_GUIDANCE =
            "notEvaluated lists the rules that had no events to run on. They did not pass: that part of the "
                    + "recording cannot be assessed, and belongs in a report as such.";
    private static final String SOURCE_GUIDANCE =
            "The rules never read your source. Check a finding against the profile and the checkout before "
                    + "acting on it.";

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
        return MicroscopeView.AUTO_ANALYSIS;
    }

    @Override
    public void followUp(NextSteps.Builder next, AutoAnalysisDashboard dashboard) {
        next.guidanceWhen(!dashboard.findings().isEmpty(), FINDINGS_GUIDANCE)
                .guidanceWhen(!dashboard.notEvaluated().isEmpty(), NOT_EVALUATED_GUIDANCE)
                .guidance(SOURCE_GUIDANCE);
    }

    /**
     * The cached result; read only once {@link AutoAnalysisStatus} says the rules have run.
     */
    @Override
    public AutoAnalysisDashboard render() {
        List<AutoAnalysisResult> results = profileManager.autoAnalysisManager().analysisResults();
        List<McpFinding> findings = AutoAnalysisFindings.findings(profileManager.info().id(), results);
        return new AutoAnalysisDashboard(
                McpFindings.countBySeverity(findings),
                findings,
                AutoAnalysisFindings.notEvaluated(results));
    }

    @Override
    public AutoAnalysisDashboard reachable(AutoAnalysisDashboard dashboard, Predicate<String> servesTool) {
        return new AutoAnalysisDashboard(dashboard.findingCounts(),
                McpFindings.reachable(dashboard.findings(), servesTool), dashboard.notEvaluated());
    }

    /** Where the rules stand for this profile; asks, never runs them. */
    public AutoAnalysisStatus status() {
        return AutoAnalysisStatus.of(profileManager.autoAnalysisManager());
    }

    /**
     * @param findingCounts how many findings of each severity, every severity present even at zero
     * @param findings      every rule that reached a verdict, the passes included, ordered by severity
     * @param notEvaluated  the rules the recording had no events for — gaps, not findings
     */
    public record AutoAnalysisDashboard(
            Map<String, Integer> findingCounts,
            List<McpFinding> findings,
            List<String> notEvaluated) {
    }

    /**
     * What {@code jvm_autoAnalysis} answers, whether the rules were cached, have just run, are still
     * running or cannot run: the same envelope as every section, with the rules' own status and the
     * operation a compute request started or joined.
     *
     * @param status      whether the rules have run; NOT_COMPUTED while a compute request is still running
     * @param operationId the compute run behind this answer; null when the result was read from the cache
     * @param operation   that run as operations_status reports it, less its result; null when there is none
     */
    public record Answer(
            AutoAnalysisStatus status,
            @McpNullable
            @McpDescription(SectionHeader.REASON)
            String reason,
            String profileId,
            @McpDescription(SectionHeader.SECTION)
            String section,
            String title,
            @McpNullable
            @McpDescription("The rules' findings; null until they have run")
            AutoAnalysisDashboard dashboard,
            @McpNullable
            String operationId,
            @McpNullable
            @McpDescription("The compute run as operations_status reports it, without its result, which is "
                    + "the dashboard here; null when the answer was read from the cache")
            McpOperationRegistry.Snapshot operation,
            McpFollowUp followUp,
            @McpDescription(SectionHeader.UI_LINK)
            String uiLink) {
    }
}
