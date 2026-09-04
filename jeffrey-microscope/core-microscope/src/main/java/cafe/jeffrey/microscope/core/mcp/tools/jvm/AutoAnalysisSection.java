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
import cafe.jeffrey.shared.common.model.Type;

import java.util.List;
import java.util.Set;

/**
 * Jeffrey's Auto Analysis: the JMC rule set run over the whole recording, as a list of findings with
 * a severity, an explanation and a suggested fix.
 * <p>
 * The cheapest first question about any profile. A few dozen rules look at everything at once — the
 * collector, the compiler, safepoints, allocation, I/O, thread state, the recording's own settings —
 * and each finding names a subsystem to go and look at, which is what turns "analyse this profile"
 * from a guess about where to start into a ranked list.
 * <p>
 * Read from the profile's cache rather than computed here, deliberately. Generating it loads the
 * whole recording through the JMC toolkit, which is bounded neither in time nor in memory by anything
 * this server controls; an MCP call that quietly does that would be a poor trade for a tool whose
 * point is being cheap. The Auto Analysis page in the Jeffrey UI computes and caches it, and every
 * call afterwards is a cache read — the same arrangement the pre-computed heap-dump reports use.
 */
public record AutoAnalysisSection(ProfileManager profileManager) implements JvmSection {

    public static final String ID = "autoAnalysis";

    private static final String TITLE = "Auto Analysis";

    /**
     * The rules run over the recording file rather than over its parsed event types, so no particular
     * event has to be present for the section to be answerable.
     */
    private static final Set<Type> EVENT_TYPES = Set.of();

    private static final List<String> NEXT_STEPS = List.of(
            "Each finding names a subsystem: follow it into the matching jvm_ section for the figures "
                    + "rather than repeating the rule's suggestion as a conclusion.",
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
        List<Finding> findings = profileManager.autoAnalysisManager().analysisResults().stream()
                .map(AutoAnalysisSection::finding)
                .toList();

        return new AutoAnalysisDashboard(findings.size(), findings);
    }

    /**
     * Whether the analysis has been computed for this profile. False means nobody has opened the Auto
     * Analysis page yet, not that the recording has nothing to report.
     */
    public boolean isComputed() {
        return !profileManager.autoAnalysisManager().analysisResults().isEmpty();
    }

    private static Finding finding(AutoAnalysisResult result) {
        return new Finding(
                result.rule(),
                result.severity() == null ? null : result.severity().name(),
                result.score(),
                result.summary(),
                result.explanation(),
                result.solution());
    }

    private record AutoAnalysisDashboard(int findingCount, List<Finding> findings) {
    }

    /**
     * @param rule     the rule that fired, which is also the subsystem to follow up in
     * @param severity how seriously the rule set took it, most severe first in the list
     * @param solution what the rule suggests doing about it — a starting point to check against the
     *                 profile, never a conclusion to repeat
     */
    private record Finding(
            String rule,
            String severity,
            String score,
            String summary,
            String explanation,
            String solution) {
    }
}
