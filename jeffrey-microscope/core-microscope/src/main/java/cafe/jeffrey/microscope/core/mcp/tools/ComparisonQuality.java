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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.mcp.AbstractMcpStreamableHttpController;
import cafe.jeffrey.profile.mcp.McpToolResult;
import cafe.jeffrey.profile.mcp.finding.McpFinding;
import cafe.jeffrey.shared.common.Json;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Whole-recording evidence for assessing a pair before interpreting its differential call tree. */
final class ComparisonQuality {

    static final String OUTPUT_SCHEMA = """
            {"type":"object","properties":{
              "schemaVersion":{"type":"integer"},"primary":{"type":"object"},"baseline":{"type":"object"},
              "scope":{"type":"string"},"replay":{"type":"object"},
              "samplingConfiguration":{"type":"array","items":{"type":"object"}},
              "commonEventTypes":{"type":"array","items":{"type":"string"}},
              "onlyInPrimary":{"type":"array","items":{"type":"string"}},
              "onlyInBaseline":{"type":"array","items":{"type":"string"}},
              "findings":{"type":"array","items":{"type":"object"}},
              "workloadNormalization":{"type":"object"},"durationNormalization":{"type":"object"},
              "truncation":{"type":"object"}},
              "required":["schemaVersion","primary","baseline","scope","replay","samplingConfiguration","workloadNormalization","truncation"]}
            """;

    private ComparisonQuality() {
    }

    static McpToolResult result(ProfileManager primary, ProfileManager baseline) {
        List<ProfileEvidence.EventEvidence> primaryEvents = ProfileEvidence.events(primary);
        List<ProfileEvidence.EventEvidence> baselineEvents = ProfileEvidence.events(baseline);
        ObjectNode root = Json.createObject().put("schemaVersion", 1).put("findingSchemaVersion", 1)
                .put("generatedAt", Instant.now().toString())
                .put("serverVersion", AbstractMcpStreamableHttpController.serverVersion())
                .put("scope", ProfileEvidence.WHOLE_RECORDING)
                .put("semantics", "Current-state recording evidence; does not certify equivalent workloads or immutable historical state")
                .put("samplingSettingsScope", ProfileEvidence.SETTINGS_SCOPE)
                .put("eventOverlapMeaning", "Observed event presence; absence alone cannot distinguish no activity from disabled instrumentation");
        ObjectNode primarySide = ProfileEvidence.identity(primary.info(), null);
        ObjectNode baselineSide = ProfileEvidence.identity(baseline.info(), null);
        primarySide.set("samplerHealth", ProfileEvidence.samplerHealth(primary));
        baselineSide.set("samplerHealth", ProfileEvidence.samplerHealth(baseline));
        root.set("primary", primarySide);
        root.set("baseline", baselineSide);
        root.putObject("replay").put("tool", "compare_quality").putObject("arguments")
                .put("profileId", primary.info().id()).put("baselineProfileId", baseline.info().id());
        root.putObject("workloadNormalization").put("available", false)
                .put("reason", "Recorded server-event counts do not establish complete operation counts or equivalent workloads; CPU samples are not request counts");
        Long primaryMs = ProfileEvidence.durationMillis(primary.info());
        Long baselineMs = ProfileEvidence.durationMillis(baseline.info());
        boolean durationAvailable = primaryMs != null && baselineMs != null && primaryMs > 0 && baselineMs > 0;
        ObjectNode duration = root.putObject("durationNormalization").put("available", durationAvailable)
                .put("method", "recording-exposure")
                .put("assumption", "Both runs must observe the same kind of work at the same rate; this evidence cannot establish that")
                .put("filteredComparisons", "Graph exports use each recording's exposure within the selected window; these metadata totals cover the whole recording");
        if (durationAvailable) {
            duration.put("baselineScaleFactor", (double) primaryMs / baselineMs);
        }
        Map<String, ProfileEvidence.EventEvidence> primaryByType = byType(primaryEvents);
        Map<String, ProfileEvidence.EventEvidence> baselineByType = byType(baselineEvents);
        List<SamplingConfiguration> settings = new ArrayList<>();
        List<McpFinding> findings = new ArrayList<>();
        Set<String> common = new LinkedHashSet<>(primaryByType.keySet());
        common.retainAll(baselineByType.keySet());
        for (String type : common) {
            ProfileEvidence.EventEvidence left = primaryByType.get(type);
            ProfileEvidence.EventEvidence right = baselineByType.get(type);
            String status = left.settings().isEmpty() || right.settings().isEmpty() ? "unknown"
                    : left.settings().equals(right.settings()) ? "matching-snapshot" : "mismatch";
            settings.add(new SamplingConfiguration(type, status, left.settings(), right.settings()));
            if (status.equals("mismatch")) {
                findings.add(McpFinding.of("comparison_configuration", type)
                        .severity(McpFinding.Severity.WARNING).title("Recorded event settings differ")
                        .detail("Configuration differences can change event volume independently of application behavior")
                        .source("compare_quality").evidence("eventType", type)
                        .evidence("primarySettings", left.settings()).evidence("baselineSettings", right.settings())
                        .nextTool("compare_list").build());
            }
        }
        Set<String> onlyPrimary = new LinkedHashSet<>(primaryByType.keySet());
        onlyPrimary.removeAll(common);
        Set<String> onlyBaseline = new LinkedHashSet<>(baselineByType.keySet());
        onlyBaseline.removeAll(common);
        EvidenceOutput output = new EvidenceOutput(root, 100);
        output.rows("findings", findings);
        output.rows("samplingConfiguration", settings);
        output.rows("commonEventTypes", List.copyOf(common));
        output.rows("onlyInPrimary", List.copyOf(onlyPrimary));
        output.rows("onlyInBaseline", List.copyOf(onlyBaseline));
        output.rows(primarySide, "observedWorkloadEvents", "primary.observedWorkloadEvents", ProfileEvidence.workload(primaryEvents));
        output.rows(baselineSide, "observedWorkloadEvents", "baseline.observedWorkloadEvents", ProfileEvidence.workload(baselineEvents));
        output.rows(primarySide, "eventTypes", "primary.eventTypes", primaryEvents);
        output.rows(baselineSide, "eventTypes", "baseline.eventTypes", baselineEvents);
        return output.result();
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

    private record SamplingConfiguration(String eventType, String status,
                                         Map<String, String> primarySettings, Map<String, String> baselineSettings) {
    }
}
