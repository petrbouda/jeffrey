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

import cafe.jeffrey.microscope.core.manager.recordings.RecordingCommitResolver;
import cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies;
import cafe.jeffrey.microscope.core.mcp.UiLinks;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.AutoAnalysisFindings;
import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.profile.common.analysis.AutoAnalysisResult;
import cafe.jeffrey.profile.feature.FeatureType;
import cafe.jeffrey.profile.manager.AutoAnalysisManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.mcp.AbstractMcpStreamableHttpController;
import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.profile.mcp.McpToolCost;
import cafe.jeffrey.profile.mcp.McpToolMeta;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.profile.mcp.ToolParamBounds;
import cafe.jeffrey.profile.mcp.finding.McpFinding;
import cafe.jeffrey.profile.mcp.finding.McpFindings;
import cafe.jeffrey.profile.panel.JfrFlamegraphPanelProvider;
import cafe.jeffrey.profile.panel.StackSampleFlamegraphPanelProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.Clock;
import java.util.List;
import java.util.Map;

/** Bounded current-state evidence returned inline, preserving the existing finding identities. */
public class ProfileEvidenceMcpTools {

    /** The filters a replay applies: the whole recording, every event type and thread. */
    record EvidenceFilters(String scope, String eventType, String threads, boolean excludeIdle,
                           boolean excludeNonJava) {
    }

    /**
     * The evidence snapshot. {@code truncation} names each bounded collection — findings,
     * capabilityGaps, notEvaluatedRules, eventTypes — with what it held and returned.
     * {@code followUp.nextTools} holds the call that reads the same current state again.
     */
    record EvidenceDocument(
            int schemaVersion,
            int findingSchemaVersion,
            @McpDescription("When this snapshot was taken, as UTC epoch milliseconds")
            long generatedAtEpochMs,
            String serverVersion,
            String semantics,
            String samplingSettingsScope,
            ProfileEvidence.Identity profile,
            EvidenceFilters filters,
            McpFollowUp followUp,
            ProfileEvidence.SamplerHealth samplerHealth,
            @McpDescription("Whether the auto-analysis rules have run; findings are theirs only when COMPUTED")
            AutoAnalysisStatus autoAnalysis,
            String findingEvidenceUnits,
            Map<String, EvidenceOutput.Truncation> truncation,
            int outputLimitChars,
            List<McpFinding> findings,
            List<ProfileCapabilityGaps.CapabilityGap> capabilityGaps,
            List<String> notEvaluatedRules,
            List<ProfileEvidence.EventEvidence> eventTypes,
            @McpDescription("The profile's page in the Microscope UI, for the user")
            String uiLink) {

        /** This document with its bounded collections, and the counts that say how they were bounded. */
        EvidenceDocument withRows(Map<String, EvidenceOutput.Truncation> truncation, List<McpFinding> findings,
                                  List<ProfileCapabilityGaps.CapabilityGap> capabilityGaps,
                                  List<String> notEvaluatedRules, List<ProfileEvidence.EventEvidence> eventTypes) {
            return new EvidenceDocument(schemaVersion, findingSchemaVersion, generatedAtEpochMs, serverVersion,
                    semantics, samplingSettingsScope, profile, filters, followUp, samplerHealth,
                    autoAnalysis, findingEvidenceUnits, truncation, outputLimitChars, findings,
                    capabilityGaps, notEvaluatedRules, eventTypes, uiLink);
        }
    }

    /** Bumped when the shape of the document changes, so a saved snapshot says what it was written to. */
    private static final int SCHEMA_VERSION = 2;
    private static final int FINDING_SCHEMA_VERSION = 1;

    /** Records per collection before the output budget has anything to say about it. */
    private static final int DEFAULT_ROWS = 100;
    private static final int MAX_ROWS = 500;

    private static final String TOOL_NAME = "profiles_evidence";
    private static final String PROFILE_ID = "profileId";
    private static final String LIMIT = "limit";
    private static final String REPLAY_WHY = "reads the same current state again";
    private static final String AUTO_ANALYSIS_TOOL = "jvm_autoAnalysis";
    private static final String COMPUTE = "compute";
    private static final String COMPUTE_WHY = "runs the auto-analysis rules this snapshot only reads; slow, it "
            + "reads the whole recording, and it may hand back an operation to follow";
    private static final String ALL = "all";
    private static final String SEMANTICS =
            "Snapshot of current profile state; the profiles_evidence call in followUp.nextTools reads current "
                    + "state again, not an immutable historical version";
    private static final String FINDING_EVIDENCE_UNITS =
            "JMC score is the rule score; other evidence keeps its source-defined units";
    private static final String FINDINGS = "findings";
    private static final String CAPABILITY_GAPS = "capabilityGaps";
    private static final String NOT_EVALUATED_RULES = "notEvaluatedRules";
    private static final String EVENT_TYPES = "eventTypes";

    private final ProfileManager manager;
    private final RecordingCommitResolver commits;
    private final ProfileCapabilityGaps capabilityGaps;
    private final Clock clock;
    private final AdvertisedFamilies advertised;

    /**
     * @param advertised the families a next call, and a finding's call, may route to
     */
    public ProfileEvidenceMcpTools(ProfileManager manager, RecordingCommitResolver commits,
                                  JfrFlamegraphPanelProvider jfrPanels, StackSampleFlamegraphPanelProvider stackPanels,
                                  Clock clock, AdvertisedFamilies advertised) {
        this.manager = manager;
        this.commits = commits;
        this.capabilityGaps = new ProfileCapabilityGaps(manager, new FlamegraphCatalog(manager, jfrPanels, stackPanels));
        this.clock = clock;
        this.advertised = advertised;
    }

    @Tool(description = "Returns a versioned evidence snapshot of the profile's current state as one "
            + "JSON document: recording identity, build, whole-recording units and denominators, "
            + "stored sampling settings, whether the auto-analysis rules have run and their evaluated "
            + "findings, capability gaps, the call that replays it and complete-record omission "
            + "counts. Instants are UTC epoch milliseconds. A live snapshot, not an immutable archive: "
            + "the returned document is what preserves this observation. Computes and changes nothing.")
    @McpOutputSchema(EvidenceDocument.class)
    @McpToolMeta(cost = McpToolCost.CHEAP)
    public McpToolResult evidence(
            @ToolParam(required = false, description = "Maximum records per evidence collection; default "
                    + DEFAULT_ROWS + ", maximum " + MAX_ROWS + ". Output size may reduce it further.")
            @ToolParamBounds(defaultValue = DEFAULT_ROWS, min = 1, max = MAX_ROWS)
            Integer limit) {
        int rows = ToolArguments.boundedLimit(limit, DEFAULT_ROWS, MAX_ROWS);
        String profileId = manager.info().id();
        AutoAnalysisManager autoAnalysis = manager.autoAnalysisManager();
        AutoAnalysisStatus status = AutoAnalysisStatus.of(autoAnalysis);
        // Read only when the rules ran, the way profiles_summary and the findings resource read them.
        List<AutoAnalysisResult> analysis = status == AutoAnalysisStatus.COMPUTED
                ? autoAnalysis.analysisResults()
                : List.of();
        List<FeatureType> disabled = ProfileDisabledFeatures.of(manager);
        McpFollowUp followUp = NextSteps.builder(advertised)
                .next(NextCalls.to(TOOL_NAME).with(PROFILE_ID, profileId).with(LIMIT, rows).why(REPLAY_WHY))
                .nextWhen(status == AutoAnalysisStatus.NOT_COMPUTED, NextCalls.to(AUTO_ANALYSIS_TOOL)
                        .with(PROFILE_ID, profileId).with(COMPUTE, true).why(COMPUTE_WHY))
                .followUp();
        EvidenceDocument skeleton = new EvidenceDocument(SCHEMA_VERSION, FINDING_SCHEMA_VERSION, clock.millis(),
                AbstractMcpStreamableHttpController.serverVersion(), SEMANTICS, ProfileEvidence.SETTINGS_SCOPE,
                ProfileEvidence.identity(manager.info(), commits.resolve(manager.info().recordingId()).orElse(null)),
                new EvidenceFilters(ProfileEvidence.WHOLE_RECORDING, ALL, ALL, false, false),
                followUp, ProfileEvidence.samplerHealth(manager), status,
                FINDING_EVIDENCE_UNITS, Map.of(), McpToolOutput.MAX_CHARS,
                List.of(), List.of(), List.of(), List.of(), UiLinks.profile(profileId));
        EvidenceOutput output = new EvidenceOutput(skeleton, rows);
        List<McpFinding> findings = output.rows(FINDINGS, McpFindings.reachable(
                AutoAnalysisFindings.findings(profileId, analysis), advertised::servesTool));
        List<ProfileCapabilityGaps.CapabilityGap> gaps = output.rows(CAPABILITY_GAPS, capabilityGaps.gaps(disabled));
        List<String> notEvaluated = output.rows(NOT_EVALUATED_RULES, AutoAnalysisFindings.notEvaluated(analysis));
        List<ProfileEvidence.EventEvidence> events = output.rows(EVENT_TYPES, ProfileEvidence.events(manager));
        return McpToolResult.of(skeleton.withRows(output.truncation(), findings, gaps, notEvaluated, events));
    }
}
