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

import cafe.jeffrey.microscope.core.manager.recordings.RecordingCommitResolver;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.AutoAnalysisFindings;
import cafe.jeffrey.profile.common.analysis.AutoAnalysisResult;
import cafe.jeffrey.profile.feature.FeatureType;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.mcp.AbstractMcpStreamableHttpController;
import cafe.jeffrey.profile.mcp.McpOutputSchema;
import cafe.jeffrey.profile.mcp.McpToolResult;
import cafe.jeffrey.profile.panel.JfrFlamegraphPanelProvider;
import cafe.jeffrey.profile.panel.StackSampleFlamegraphPanelProvider;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Downloadable, bounded current-state evidence, preserving the existing finding identities. */
public class ProfileEvidenceMcpTools {

    public static final String OUTPUT_SCHEMA = """
            {"type":"object","properties":{
              "schemaVersion":{"type":"integer"},"findingSchemaVersion":{"type":"integer"},
              "generatedAt":{"type":"string"},"serverVersion":{"type":"string"},
              "semantics":{"type":"string"},"profile":{"type":"object"},"filters":{"type":"object"},
              "replay":{"type":"object"},"samplerHealth":{"type":"object"},
              "eventTypes":{"type":"array","items":{"type":"object"}},
              "findings":{"type":"array","items":{"type":"object"}},
              "capabilityGaps":{"type":"array","items":{"type":"object"}},
              "notEvaluatedRules":{"type":"array","items":{"type":"string"}},
              "truncation":{"type":"object"}},
              "required":["schemaVersion","profile","filters","replay","eventTypes","findings","capabilityGaps","truncation"]}
            """;

    private final ProfileManager manager;
    private final RecordingCommitResolver commits;
    private final ProfileCapabilityGaps capabilityGaps;

    public ProfileEvidenceMcpTools(ProfileManager manager, RecordingCommitResolver commits,
                                  JfrFlamegraphPanelProvider jfrPanels, StackSampleFlamegraphPanelProvider stackPanels) {
        this.manager = manager;
        this.commits = commits;
        this.capabilityGaps = new ProfileCapabilityGaps(manager, new FlamegraphCatalog(manager, jfrPanels, stackPanels));
    }

    @Tool(description = "Download a versioned evidence snapshot of the profile's current state: recording identity, "
            + "build, whole-recording units and denominators, stored sampling settings, evaluated findings and capability gaps. "
            + "Contains replay arguments and complete-record omission counts. This is a live snapshot, not an immutable archive; "
            + "save the returned document to preserve this observation. No analysis is computed or mutated.")
    @McpOutputSchema(OUTPUT_SCHEMA)
    public McpToolResult evidence(
            @ToolParam(required = false, description = "Maximum records per evidence collection; default 100, maximum 500. Output size may reduce it further.")
            Integer limit) {
        int rows = ToolArguments.boundedLimit(limit, 100, 500);
        ObjectNode root = Json.createObject().put("schemaVersion", 1).put("findingSchemaVersion", 1)
                .put("generatedAt", Instant.now().toString())
                .put("serverVersion", AbstractMcpStreamableHttpController.serverVersion())
                .put("semantics", "Snapshot of current profile state; replay reads current state again, not an immutable historical version")
                .put("samplingSettingsScope", ProfileEvidence.SETTINGS_SCOPE);
        root.set("profile", ProfileEvidence.identity(manager.info(),
                commits.resolve(manager.info().recordingId()).orElse(null)));
        root.putObject("filters").put("scope", ProfileEvidence.WHOLE_RECORDING)
                .put("eventType", "all").put("threads", "all")
                .put("excludeIdle", false).put("excludeNonJava", false);
        ObjectNode replay = root.putObject("replay").put("tool", "profiles_evidence");
        replay.putObject("arguments").put("profileId", manager.info().id()).put("limit", rows);
        root.set("samplerHealth", ProfileEvidence.samplerHealth(manager));
        List<AutoAnalysisResult> analysis = manager.autoAnalysisManager().analysisResults();
        root.put("autoAnalysisComputed", manager.autoAnalysisManager().isComputed());
        root.put("findingEvidenceUnits", "JMC score is the rule score; other evidence keeps its source-defined units");
        List<FeatureType> disabled = new ArrayList<>(manager.featuresManager().getDisabledFeatures());
        if (!manager.heapDumpManager().heapDumpExists() || !manager.heapDumpManager().isCacheReady()) {
            disabled.add(FeatureType.HEAP_DUMP);
        }
        if (manager.info().eventSource() == RecordingEventSource.PPROF) {
            disabled.add(FeatureType.SUBSECOND);
            disabled.add(FeatureType.TIMESERIES);
        }
        EvidenceOutput output = new EvidenceOutput(root, rows);
        output.rows("findings", AutoAnalysisFindings.findings(analysis));
        output.rows("capabilityGaps", capabilityGaps.gaps(disabled));
        output.rows("notEvaluatedRules", AutoAnalysisFindings.notEvaluated(analysis));
        output.rows("eventTypes", ProfileEvidence.events(manager));
        return output.result();
    }
}
