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
import cafe.jeffrey.microscope.mcp.protocol.McpMinimum;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.mcp.AbstractMcpStreamableHttpController;
import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.profile.mcp.finding.McpFinding;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Whole-recording evidence for assessing a pair before interpreting its differential call tree. */
final class ComparisonQuality {

    /** Bumped when the shape of the document changes; read beside the evidence snapshot's own. */
    private static final int SCHEMA_VERSION = 3;
    private static final int FINDING_SCHEMA_VERSION = 1;

    /** Records per collection. Fixed rather than a caller's choice: the verdict is a page, not a dump. */
    private static final int MAX_ROWS = 100;

    /**
     * The most the verdict serialises to. Well under {@link McpToolOutput#MAX_CHARS}: the tool declares
     * no large result, so a client applies its default limit to it — Claude Code's is about 25,000
     * tokens, which an 80,000-character verdict passed, and the client then filed the answer away where
     * the agent could not read it, so the comparison went on without its quality check. Sixty thousand
     * characters of JSON is under that limit with room to spare; past it, whole rows go, and
     * {@code truncation} says so.
     */
    static final int OUTPUT_LIMIT_CHARS = 60_000;

    private static final String EVENT_COUNT_DEFINITION =
            "Each side's samples are all recorded events of the type over the whole recording; not a filtered "
                    + "call-tree denominator";

    private static final String TOOL_NAME = "compare_quality";
    private static final String PROFILE_ID = "profileId";
    private static final String BASELINE_PROFILE_ID = "baselineProfileId";
    private static final String REPLAY_WHY = "reads the same pair again";
    private static final String SEMANTICS =
            "Current-state recording evidence; does not certify equivalent workloads or immutable historical state";
    private static final String EVENT_OVERLAP_MEANING =
            "Observed event presence; absence alone cannot distinguish no activity from disabled instrumentation";
    private static final String NO_WORKLOAD_NORMALIZATION =
            "Recorded server-event counts do not establish complete operation counts or equivalent workloads; "
                    + "CPU samples are not request counts";
    private static final String RECORDING_EXPOSURE = "recording-exposure";
    private static final String DURATION_ASSUMPTION =
            "Both runs must observe the same kind of work at the same rate; this evidence cannot establish that";
    private static final String FILTERED_COMPARISONS =
            "Graph exports use each recording's exposure within the selected window; these metadata totals "
                    + "cover the whole recording";

    private static final String FINDINGS = "findings";
    private static final String SAMPLING_CONFIGURATION = "samplingConfiguration";
    private static final String COMMON_EVENT_TYPES = "commonEventTypes";
    private static final String ONLY_IN_PRIMARY = "onlyInPrimary";
    private static final String ONLY_IN_BASELINE = "onlyInBaseline";
    private static final String PRIMARY_WORKLOAD = "primary.observedWorkloadEvents";
    private static final String BASELINE_WORKLOAD = "baseline.observedWorkloadEvents";
    private static final String PRIMARY_EVENT_TYPES = "primary.eventTypes";
    private static final String BASELINE_EVENT_TYPES = "baseline.eventTypes";

    private static final String CONFIGURATION_CATEGORY = "comparison_configuration";
    private static final String NEXT_TOOL = "compare_list";
    private static final String COMPARABLE_WHY = "lists the event types the two profiles have in common";

    /** How the stored sampling settings of one event type compare across the pair. */
    enum SettingsStatus {
        /** One side stored no settings for it. */
        UNKNOWN,
        /** Both merged settings snapshots are the same. */
        MATCHING_SNAPSHOT,
        /** They differ, which can change event volume independently of the application. */
        MISMATCH
    }

    /**
     * One event type whose stored settings do not match across the pair — a mismatch, or one side with
     * none — with both sides' settings. A type whose settings match is counted, not listed.
     */
    record SamplingConfiguration(String eventType, SettingsStatus status,
                                 Map<String, String> primarySettings, Map<String, String> baselineSettings) {
    }

    /**
     * What one side recorded of one event type: the volume a comparison weighs. Its settings are in
     * {@code samplingConfiguration}, compared with the other side's, rather than repeated per side.
     */
    record ComparedEvent(
            String eventType,
            @McpNullable
            String label,
            @McpMinimum(0)
            long samples,
            @McpNullable
            Long weight,
            @McpNullable
            String weightUnit) {

        static ComparedEvent of(ProfileEvidence.EventEvidence event) {
            return new ComparedEvent(event.eventType(), event.label(), event.samples(), event.weight(),
                    event.weightUnit());
        }
    }

    /** One side of the pair: its identity, as in the evidence snapshot, with what was recorded for it. */
    record ComparedProfile(
            ProfileEvidence.Identity profile,
            ProfileEvidence.SamplerHealth samplerHealth,
            List<ProfileEvidence.WorkloadEvents> observedWorkloadEvents,
            List<ComparedEvent> eventTypes) {

        ComparedProfile withRows(List<ProfileEvidence.WorkloadEvents> observedWorkloadEvents,
                                 List<ComparedEvent> eventTypes) {
            return new ComparedProfile(profile, samplerHealth, observedWorkloadEvents, eventTypes);
        }
    }

    record WorkloadNormalization(boolean available, String reason) {
    }

    record DurationNormalization(
            boolean available,
            String method,
            String assumption,
            String filteredComparisons,
            @McpNullable
            @McpDescription("Primary duration over baseline duration; null when either duration is unknown")
            Double baselineScaleFactor) {
    }

    /**
     * The pair verdict. {@code truncation} names each bounded collection — the top-level lists and each
     * side's {@code observedWorkloadEvents} and {@code eventTypes} as {@code primary.…}/{@code baseline.…}.
     * {@code followUp.nextTools} holds the call that reads the same pair again; {@code uiLink} is the
     * pair's page for the user, the differential grid opened on this baseline.
     */
    record QualityDocument(
            int schemaVersion,
            int findingSchemaVersion,
            @McpDescription("When this verdict was taken, as UTC epoch milliseconds")
            long generatedAtEpochMs,
            String serverVersion,
            String scope,
            String semantics,
            String samplingSettingsScope,
            String eventOverlapMeaning,
            @McpDescription("What each side's eventTypes samples count")
            String eventCountDefinition,
            ComparedProfile primary,
            ComparedProfile baseline,
            McpFollowUp followUp,
            @McpDescription("The differential flamegraphs of this pair in the Microscope UI, for the user")
            String uiLink,
            WorkloadNormalization workloadNormalization,
            DurationNormalization durationNormalization,
            Map<String, EvidenceOutput.Truncation> truncation,
            int outputLimitChars,
            List<McpFinding> findings,
            @McpDescription("The common event types whose stored settings differ (MISMATCH) or are missing on "
                    + "one side (UNKNOWN), mismatches first, with both sides' settings")
            List<SamplingConfiguration> samplingConfiguration,
            @McpMinimum(0)
            @McpDescription("How many common event types have matching stored settings; they are not listed")
            int matchingSettings,
            List<String> commonEventTypes,
            List<String> onlyInPrimary,
            List<String> onlyInBaseline) {

        /** This verdict with its bounded collections, and the counts that say how they were bounded. */
        QualityDocument withRows(Map<String, EvidenceOutput.Truncation> truncation, ComparedProfile primary,
                                 ComparedProfile baseline, List<McpFinding> findings,
                                 List<SamplingConfiguration> samplingConfiguration, List<String> commonEventTypes,
                                 List<String> onlyInPrimary, List<String> onlyInBaseline) {
            return new QualityDocument(schemaVersion, findingSchemaVersion, generatedAtEpochMs, serverVersion, scope,
                    semantics, samplingSettingsScope, eventOverlapMeaning, eventCountDefinition, primary, baseline,
                    followUp, uiLink, workloadNormalization, durationNormalization, truncation, outputLimitChars,
                    findings, samplingConfiguration, matchingSettings, commonEventTypes, onlyInPrimary,
                    onlyInBaseline);
        }
    }

    private ComparisonQuality() {
    }

    static McpToolResult result(ProfileManager primary, ProfileManager baseline, Clock clock) {
        List<ProfileEvidence.EventEvidence> primaryEvents = ProfileEvidence.events(primary);
        List<ProfileEvidence.EventEvidence> baselineEvents = ProfileEvidence.events(baseline);
        ComparedProfile primarySide = new ComparedProfile(ProfileEvidence.identity(primary.info(), null),
                ProfileEvidence.samplerHealth(primary), List.of(), List.of());
        ComparedProfile baselineSide = new ComparedProfile(ProfileEvidence.identity(baseline.info(), null),
                ProfileEvidence.samplerHealth(baseline), List.of(), List.of());
        McpFollowUp followUp = new McpFollowUp(List.of(NextCalls.to(TOOL_NAME)
                .with(PROFILE_ID, primary.info().id()).with(BASELINE_PROFILE_ID, baseline.info().id())
                .why(REPLAY_WHY)), List.of());
        Long primaryMs = ProfileEvidence.durationMillis(primary.info());
        Long baselineMs = ProfileEvidence.durationMillis(baseline.info());
        boolean durationAvailable = primaryMs != null && baselineMs != null && primaryMs > 0 && baselineMs > 0;
        DurationNormalization duration = new DurationNormalization(durationAvailable, RECORDING_EXPOSURE,
                DURATION_ASSUMPTION, FILTERED_COMPARISONS,
                durationAvailable ? (double) primaryMs / baselineMs : null);

        Map<String, ProfileEvidence.EventEvidence> primaryByType = byType(primaryEvents);
        Map<String, ProfileEvidence.EventEvidence> baselineByType = byType(baselineEvents);
        List<SamplingConfiguration> settings = new ArrayList<>();
        int matching = 0;
        List<McpFinding> findings = new ArrayList<>();
        Set<String> common = new LinkedHashSet<>(primaryByType.keySet());
        common.retainAll(baselineByType.keySet());
        for (String type : common) {
            ProfileEvidence.EventEvidence left = primaryByType.get(type);
            ProfileEvidence.EventEvidence right = baselineByType.get(type);
            SettingsStatus status = left.settings().isEmpty() || right.settings().isEmpty() ? SettingsStatus.UNKNOWN
                    : left.settings().equals(right.settings()) ? SettingsStatus.MATCHING_SNAPSHOT : SettingsStatus.MISMATCH;
            if (status == SettingsStatus.MATCHING_SNAPSHOT) {
                matching++;
            } else {
                settings.add(new SamplingConfiguration(type, status, left.settings(), right.settings()));
            }
            if (status == SettingsStatus.MISMATCH) {
                findings.add(McpFinding.of(CONFIGURATION_CATEGORY, type)
                        .severity(McpFinding.Severity.WARNING).title("Recorded event settings differ")
                        .detail("Configuration differences can change event volume independently of application behavior")
                        .source(TOOL_NAME).evidence("eventType", type)
                        .evidence("primarySettings", left.settings()).evidence("baselineSettings", right.settings())
                        .nextTool(NextCalls.to(NEXT_TOOL).with(PROFILE_ID, primary.info().id())
                                .with(BASELINE_PROFILE_ID, baseline.info().id()).why(COMPARABLE_WHY))
                        .build());
            }
        }
        settings.sort(Comparator.comparing(row -> row.status() != SettingsStatus.MISMATCH));
        Set<String> onlyPrimary = new LinkedHashSet<>(primaryByType.keySet());
        onlyPrimary.removeAll(common);
        Set<String> onlyBaseline = new LinkedHashSet<>(baselineByType.keySet());
        onlyBaseline.removeAll(common);

        QualityDocument skeleton = new QualityDocument(SCHEMA_VERSION, FINDING_SCHEMA_VERSION, clock.millis(),
                AbstractMcpStreamableHttpController.serverVersion(), ProfileEvidence.WHOLE_RECORDING, SEMANTICS,
                ProfileEvidence.SETTINGS_SCOPE, EVENT_OVERLAP_MEANING, EVENT_COUNT_DEFINITION, primarySide,
                baselineSide, followUp, CompareMcpTools.pairLink(primary.info().id(), baseline.info().id()),
                new WorkloadNormalization(false, NO_WORKLOAD_NORMALIZATION), duration, Map.of(),
                OUTPUT_LIMIT_CHARS, List.of(), List.of(), matching, List.of(), List.of(), List.of());
        EvidenceOutput output = new EvidenceOutput(skeleton, MAX_ROWS, OUTPUT_LIMIT_CHARS);
        List<McpFinding> keptFindings = output.rows(FINDINGS, findings);
        List<SamplingConfiguration> keptSettings = output.rows(SAMPLING_CONFIGURATION, settings);
        List<String> keptCommon = output.rows(COMMON_EVENT_TYPES, List.copyOf(common));
        List<String> keptOnlyPrimary = output.rows(ONLY_IN_PRIMARY, List.copyOf(onlyPrimary));
        List<String> keptOnlyBaseline = output.rows(ONLY_IN_BASELINE, List.copyOf(onlyBaseline));
        List<ProfileEvidence.WorkloadEvents> primaryWorkload =
                output.rows(PRIMARY_WORKLOAD, ProfileEvidence.workload(primaryEvents));
        List<ProfileEvidence.WorkloadEvents> baselineWorkload =
                output.rows(BASELINE_WORKLOAD, ProfileEvidence.workload(baselineEvents));
        List<ComparedEvent> primaryTypes = output.rows(PRIMARY_EVENT_TYPES,
                primaryEvents.stream().map(ComparedEvent::of).toList());
        List<ComparedEvent> baselineTypes = output.rows(BASELINE_EVENT_TYPES,
                baselineEvents.stream().map(ComparedEvent::of).toList());
        return McpToolResult.of(skeleton.withRows(output.truncation(),
                primarySide.withRows(primaryWorkload, primaryTypes),
                baselineSide.withRows(baselineWorkload, baselineTypes),
                keptFindings, keptSettings, keptCommon, keptOnlyPrimary, keptOnlyBaseline));
    }

    private static Map<String, ProfileEvidence.EventEvidence> byType(List<ProfileEvidence.EventEvidence> events) {
        Map<String, ProfileEvidence.EventEvidence> result = new LinkedHashMap<>();
        for (ProfileEvidence.EventEvidence event : events) {
            if (event.samples() > 0) {
                result.put(event.eventType(), event);
            }
        }
        return result;
    }
}
