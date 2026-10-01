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
import cafe.jeffrey.profile.mcp.McpToolWeight;
import cafe.jeffrey.profile.mcp.McpToolWeights;
import cafe.jeffrey.profile.mcp.McpToolWeights.ToolClass;

import java.util.List;
import java.util.Set;

/**
 * Every class that declares Microscope's tools, with the family it is advertised under — the list
 * the toolset assembler registers, kept here once so a next call can carry the weight of the tool it
 * names without the code that builds it stating that weight again.
 * <p>
 * {@code McpToolCatalogueTest} holds the names read from it to exactly the tools the assembled
 * toolset advertises, so a class added to the assembler and forgotten here fails the build.
 */
public final class McpToolCatalogue {

    private static final List<ToolClass> CLASSES = List.of(
            new ToolClass(ProfilesMcpTools.class, AdvertisedFamilies.PROFILES),
            new ToolClass(ProfileEvidenceMcpTools.class, AdvertisedFamilies.PROFILES),
            new ToolClass(ProfileMcpTools.class, AdvertisedFamilies.PROFILES),
            new ToolClass(OperationsMcpTools.class, AdvertisedFamilies.OPERATIONS),
            new ToolClass(EventTypeMcpTools.class, AdvertisedFamilies.JFR),
            new ToolClass(DuckDbMcpTools.class, AdvertisedFamilies.JFR),
            new ToolClass(FlamegraphMcpTools.class, AdvertisedFamilies.FLAMEGRAPH),
            new ToolClass(CompareMcpTools.class, AdvertisedFamilies.COMPARE),
            new ToolClass(TracesMcpTools.class, AdvertisedFamilies.TRACES),
            new ToolClass(TraceAttributesMcpTools.class, AdvertisedFamilies.TRACES),
            new ToolClass(JvmMcpTools.class, AdvertisedFamilies.JVM),
            new ToolClass(HttpMcpTools.class, AdvertisedFamilies.HTTP),
            new ToolClass(JdbcMcpTools.class, AdvertisedFamilies.JDBC),
            new ToolClass(GrpcMcpTools.class, AdvertisedFamilies.GRPC),
            new ToolClass(MethodTracingMcpTools.class, AdvertisedFamilies.METHOD_TRACING),
            new ToolClass(IoMcpTools.class, AdvertisedFamilies.IO),
            new ToolClass(BlockingMcpTools.class, AdvertisedFamilies.BLOCKING),
            new ToolClass(TimelineMcpTools.class, AdvertisedFamilies.TIMELINE),
            new ToolClass(MemoryMcpTools.class, AdvertisedFamilies.MEMORY),
            new ToolClass(HeapDiffMcpTools.class, AdvertisedFamilies.HEAP),
            new ToolClass(HeapOqlMcpTools.class, AdvertisedFamilies.HEAP),
            new ToolClass(HeapDumpMcpTools.class, AdvertisedFamilies.HEAP),
            new ToolClass(HeapComputeMcpTools.class, AdvertisedFamilies.HEAP),
            new ToolClass(RecordingsMcpTools.class, AdvertisedFamilies.RECORDINGS),
            new ToolClass(IdeMcpTools.class, AdvertisedFamilies.IDE),
            new ToolClass(HubsMcpTools.class, AdvertisedFamilies.HUBS),
            new ToolClass(HubsArtifactsMcpTools.class, AdvertisedFamilies.HUBS));

    private static final McpToolWeights WEIGHTS = McpToolWeights.read(CLASSES);

    private McpToolCatalogue() {
    }

    /** The weight of the named tool; a name no tool class declares is refused. */
    public static McpToolWeight weightOf(String tool) {
        return WEIGHTS.of(tool);
    }

    /** Every tool name the classes declare, sorted. */
    public static Set<String> tools() {
        return WEIGHTS.tools();
    }
}
