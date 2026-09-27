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

import cafe.jeffrey.microscope.core.mcp.tools.BlockingMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.CatalogueSpecs;
import cafe.jeffrey.microscope.core.mcp.tools.DuckDbMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.FlamegraphMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.IoMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.JvmMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.MemoryMcpTools;
import cafe.jeffrey.microscope.mcp.protocol.McpToolSpec;
import cafe.jeffrey.profile.common.analysis.AnalysisResult;
import cafe.jeffrey.profile.common.analysis.AutoAnalysisResult;
import cafe.jeffrey.profile.mcp.McpNextToolConformance;
import cafe.jeffrey.profile.mcp.finding.McpFinding;
import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AutoAnalysisFindingsTest {

    private static final String PROFILE_ID = "p-1";

    /** Every family a rule topic routes to, as the endpoint advertises it. */
    private static final List<McpToolSpec> ROUTED_FAMILIES = CatalogueSpecs.of(
            CatalogueSpecs.profileScoped(JvmMcpTools.class, "jvm"),
            CatalogueSpecs.profileScoped(MemoryMcpTools.class, "memory"),
            CatalogueSpecs.profileScoped(BlockingMcpTools.class, "blocking"),
            CatalogueSpecs.profileScoped(FlamegraphMcpTools.class, "flamegraph"),
            CatalogueSpecs.profileScoped(IoMcpTools.class, "io"),
            CatalogueSpecs.profileScoped(DuckDbMcpTools.class, "jfr"));

    private static AutoAnalysisResult fired(String topic) {
        return new AutoAnalysisResult("Rule " + topic, AnalysisResult.Severity.WARNING, "explanation",
                "summary", "solution", "70", topic);
    }

    /**
     * Each topic's call is one a client can make as it stands: an advertised tool, on this profile,
     * with the argument that picks the right page where the tool has more than one.
     */
    @Test
    void everyTopicRoutesToACallThatCanBeFollowed() {
        for (String topic : AutoAnalysisFindings.routedTopics()) {
            McpFinding finding = AutoAnalysisFindings.finding(PROFILE_ID, fired(topic)).orElseThrow();

            assertEquals(PROFILE_ID, finding.nextTool().arguments().value().path("profileId").asString(), topic);
            assertEquals(1, McpNextToolConformance.assertFollowable(Json.toTree(finding), ROUTED_FAMILIES), topic);
        }
    }

    @Test
    void aTopicThatNeedsAPageCarriesIt() {
        McpFinding finding = AutoAnalysisFindings.finding(PROFILE_ID, fired("gc_configuration")).orElseThrow();

        assertEquals("jvm_gcDetail", finding.nextTool().tool());
        assertEquals("CONFIGURATION", finding.nextTool().arguments().value().path("page").asString());
    }

    @Test
    void anUnknownTopicHasNoCall() {
        McpFinding finding = AutoAnalysisFindings.finding(PROFILE_ID, fired("not_a_topic")).orElseThrow();

        assertNull(finding.nextTool());
    }
}
