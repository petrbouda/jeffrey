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

import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;
import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.profile.mcp.finding.McpFinding;

import java.util.List;
import java.util.Map;

/**
 * The judgements one profile already holds: the document {@code jeffrey://profile/{profileId}/findings}
 * serves. Read from what is cached and stored; reading it never runs the analysis.
 * <p>
 * Bounded by the rule set: one finding per JMC rule that reached a verdict, one per rule that could not
 * run, the container verdict and one gap per missing capability -- about 15 KB on a real profile.
 *
 * @param status            whether the auto-analysis rules have run for this profile
 * @param reason            why the rules' findings are missing, or null when they are there
 * @param severityCounts    how many findings of each severity, every severity present even at zero
 * @param findings          the auto-analysis findings merged with the container throttling verdict,
 *                          most severe first; only the container verdict while the rules have not run
 * @param notEvaluatedRules the rules the recording had no events for: gaps, not passes
 * @param capabilityGaps    what this recording cannot answer, in words, with what would close each gap
 * @param followUp          the call that runs the rules when they have not run and can
 * @param uiLink            the profile's Auto Analysis page in Microscope, for the user
 */
public record ProfileFindings(
        String profileId,
        AutoAnalysisStatus status,
        @McpNullable
        @McpDescription("Why the auto-analysis findings are missing; null when status is COMPUTED")
        String reason,
        @McpDescription("How many findings of each severity, every severity present even at zero")
        Map<String, Integer> severityCounts,
        List<McpFinding> findings,
        @McpDescription("Auto-analysis rules the recording had no events for: gaps, not passes")
        List<String> notEvaluatedRules,
        List<ProfileCapabilityGaps.CapabilityGap> capabilityGaps,
        McpFollowUp followUp,
        @McpDescription("The profile's Auto Analysis page in the Microscope UI, for the user")
        String uiLink) {
}
