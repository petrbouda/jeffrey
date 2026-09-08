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

import cafe.jeffrey.profile.common.analysis.AutoAnalysisResult;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.mcp.finding.McpFinding;
import cafe.jeffrey.profile.mcp.finding.McpFindings;
import cafe.jeffrey.shared.common.model.Type;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The JMC rule set over the whole recording, as findings in the shared shape.
 * <p>
 * The rules are the one place in the surface that makes a judgement rather than reporting a figure,
 * which is why their output is the shared finding record rather than a dashboard of its own: a
 * reader merges it with what the dashboards say, by id, instead of re-parsing prose. A rule that
 * could not run on this recording is listed apart from the findings, because "did not run" and
 * "passed" must never read the same.
 */
public record AutoAnalysisSection(ProfileManager profileManager) implements JvmSection {

    public static final String ID = "autoAnalysis";
    private static final String TITLE = "Auto Analysis";

    private static final Set<Type> EVENT_TYPES = Set.of();

    private static final List<String> NEXT_STEPS = List.of(
            "Each finding names its category and a nextTool: follow it there for the figures rather than "
                    + "repeating the rule's action as a conclusion. A rule applies fixed thresholds that "
                    + "know nothing about this service's normal behaviour, so a fired rule is a lead.",
            "notEvaluated lists the rules that had no events to run on. They did not pass — that part of "
                    + "the recording cannot be assessed, and belongs in a report as such.",
            "The rules never read your source. Check a finding against the profile and the checkout before "
                    + "acting on it.");

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
        List<AutoAnalysisResult> results = profileManager.autoAnalysisManager().analysisResults();
        List<McpFinding> findings = AutoAnalysisFindings.findings(results);
        return new AutoAnalysisDashboard(
                McpFindings.countBySeverity(findings),
                findings,
                AutoAnalysisFindings.notEvaluated(results));
    }

    public boolean isComputed() {
        return !profileManager.autoAnalysisManager().analysisResults().isEmpty();
    }

    /**
     * @param findingCounts how many findings of each severity, every severity present even at zero
     * @param findings      every rule that reached a verdict, the passes included, ordered by severity
     * @param notEvaluated  the rules the recording had no events for — gaps, not findings
     */
    private record AutoAnalysisDashboard(
            Map<String, Integer> findingCounts,
            List<McpFinding> findings,
            List<String> notEvaluated) {
    }
}
