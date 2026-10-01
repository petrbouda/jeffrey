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

import cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies;
import cafe.jeffrey.microscope.core.mcp.MicroscopeView;
import cafe.jeffrey.microscope.core.mcp.UiLinks;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.AutoAnalysisFindings;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.ContainerSection;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.JvmSections;
import cafe.jeffrey.profile.common.analysis.AutoAnalysisResult;
import cafe.jeffrey.profile.manager.AutoAnalysisManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.mcp.finding.McpFinding;
import cafe.jeffrey.profile.mcp.finding.McpFindings;
import cafe.jeffrey.profile.panel.JfrFlamegraphPanelProvider;
import cafe.jeffrey.profile.panel.StackSampleFlamegraphPanelProvider;

import java.util.List;

/**
 * Reads {@link ProfileFindings} for one profile: the cached auto-analysis, the container throttling
 * verdict and the capability gaps, merged the way {@code jvm_autoAnalysis}, {@code jvm_container} and
 * {@code profiles_summary} each report their part.
 * <p>
 * Never starts work. The rules read the whole recording through the JMC toolkit, which is slow and
 * unbounded in memory, and a resource read has no task or operation to hand back while it runs. So
 * this asks only whether the rules have run; when they have not, it says so and names the call that
 * would run them.
 */
public final class ProfileFindingsReader {

    /** The Microscope page the rules' findings are drawn on. */
    static final MicroscopeView AUTO_ANALYSIS_VIEW = MicroscopeView.AUTO_ANALYSIS;

    private static final String AUTO_ANALYSIS_TOOL = "jvm_autoAnalysis";
    private static final String PROFILE_ID = "profileId";
    private static final String COMPUTE = "compute";
    private static final String COMPUTE_WHY = "runs the auto-analysis rules this resource only reads; slow, "
            + "it reads the whole recording, and it may hand back an operation to follow";

    private static final String NOT_COMPUTED_REASON = "The auto-analysis rules have not run for this profile, "
            + "and reading this resource never runs them, so only the container verdict is listed. "
            + "followUp.nextTools names the call that runs them.";
    private static final String CANNOT_COMPUTE_REASON = "The auto-analysis rules have not run and cannot: "
            + "no JFR recording file is available for this profile: it was removed, or the profile was not "
            + "imported from a JFR recording. Only the container verdict is listed.";

    private final ProfileManager profileManager;
    private final ProfileCapabilityGaps capabilityGaps;
    private final AdvertisedFamilies advertised;

    /**
     * @param advertised the families the follow-up call may route to
     */
    public ProfileFindingsReader(
            ProfileManager profileManager,
            JfrFlamegraphPanelProvider jfrPanelProvider,
            StackSampleFlamegraphPanelProvider stackSamplePanelProvider,
            AdvertisedFamilies advertised) {
        this.profileManager = profileManager;
        this.capabilityGaps = new ProfileCapabilityGaps(
                profileManager, new FlamegraphCatalog(profileManager, jfrPanelProvider, stackSamplePanelProvider));
        this.advertised = advertised;
    }

    /**
     * Builds the page link from the request being served, so it is called while one is bound.
     */
    public ProfileFindings read() {
        String profileId = profileManager.info().id();
        AutoAnalysisManager autoAnalysis = profileManager.autoAnalysisManager();
        AutoAnalysisStatus status = AutoAnalysisStatus.of(autoAnalysis);
        // Asked of the manager's isComputed, never inferred from an empty result: a run that flagged
        // nothing caches an empty list, and that is an answer, not a missing one.
        List<AutoAnalysisResult> results = status == AutoAnalysisStatus.COMPUTED
                ? autoAnalysis.analysisResults()
                : List.of();
        List<McpFinding> findings = McpFindings.reachable(
                McpFindings.merge(AutoAnalysisFindings.findings(profileId, results), containerFindings()),
                advertised::servesTool);
        return new ProfileFindings(
                profileId,
                status,
                reason(status),
                McpFindings.countBySeverity(findings),
                findings,
                AutoAnalysisFindings.notEvaluated(results),
                capabilityGaps.gaps(ProfileDisabledFeatures.of(profileManager)),
                NextSteps.builder(advertised)
                        .nextWhen(status == AutoAnalysisStatus.NOT_COMPUTED, NextCalls.to(AUTO_ANALYSIS_TOOL)
                                .with(PROFILE_ID, profileId)
                                .with(COMPUTE, true)
                                .why(COMPUTE_WHY))
                        .followUp(),
                UiLinks.view(profileId, AUTO_ANALYSIS_VIEW));
    }

    private static String reason(AutoAnalysisStatus status) {
        return switch (status) {
            case COMPUTED -> null;
            case NOT_COMPUTED -> NOT_COMPUTED_REASON;
            case CANNOT_COMPUTE -> CANNOT_COMPUTE_REASON;
        };
    }

    /**
     * The throttling verdict, when the recording carries the container events it is drawn from; a
     * recording without them has no verdict to give, which the capability gaps already say.
     */
    private List<McpFinding> containerFindings() {
        JvmSections sections = JvmSections.standard(profileManager);
        ContainerSection container = sections.get(ContainerSection.ID, ContainerSection.class);
        return sections.isAvailable(container) ? container.findings() : List.of();
    }
}
