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

import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.profile.mcp.McpNextTool;
import cafe.jeffrey.profile.mcp.finding.McpFinding;
import cafe.jeffrey.profile.mcp.finding.McpFindings;

import java.util.List;

/**
 * Filled-in resource documents for the tests that route, serve and link them without reading a
 * profile: the shape each reader produces, with one of everything in it.
 */
public final class ProfileDocumentFixtures {

    private static final String LINK = "http://localhost/profiles/%s/%s";
    private static final String FIELDS_NOTE = "The 'fields' column contains event-specific data as JSON.";

    private ProfileDocumentFixtures() {
    }

    public static ProfileSchema schema(String profileId) {
        return new ProfileSchema(
                profileId,
                List.of(new ProfileSchema.Relation("events", true,
                        List.of(new ProfileSchema.Column("event_type", "VARCHAR", true),
                                new ProfileSchema.Column("fields", "JSON", true)),
                        FIELDS_NOTE),
                        new ProfileSchema.Relation("threads", false,
                                List.of(new ProfileSchema.Column("thread_hash", "BIGINT", false)), null)),
                List.of(new ProfileSchema.EventType("jdk.ExecutionSample", "Method Profiling Sample", null, 3, 3)),
                LINK.formatted(profileId, ProfileSchemaReader.EVENT_TYPES_VIEW.path()));
    }

    /** Auto-analysis not computed: a container verdict, one gap, and the call that would run the rules. */
    public static ProfileFindings findings(String profileId) {
        List<McpFinding> findings = List.of(McpFinding.of("container", "cpu-throttling")
                .severity(McpFinding.Severity.WARNING)
                .title("CPU throttled")
                .source("jvm_container")
                .evidence("throttledPeriods", 250)
                .build());
        return new ProfileFindings(
                profileId,
                AutoAnalysisStatus.NOT_COMPUTED,
                "Auto-analysis has not run for this profile.",
                McpFindings.countBySeverity(findings),
                findings,
                List.of(),
                List.of(new ProfileCapabilityGaps.CapabilityGap("autoAnalysis", "The rules did not run.", null)),
                new McpFollowUp(List.of(NextCalls.to("jvm_autoAnalysis")
                        .with("profileId", profileId)
                        .with("compute", true)
                        .why("runs the rules this resource only reads")), List.of()),
                LINK.formatted(profileId, ProfileFindingsReader.AUTO_ANALYSIS_VIEW.path()));
    }
}
