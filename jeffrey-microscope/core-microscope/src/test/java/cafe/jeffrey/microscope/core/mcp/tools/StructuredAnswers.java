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

import cafe.jeffrey.microscope.core.manager.hub.HubsManager;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies;
import cafe.jeffrey.microscope.core.mcp.AdvertisedFamiliesFixture;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpSchemaGenerator;
import cafe.jeffrey.microscope.mcp.protocol.McpToolOutcome;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.mcp.protocol.McpToolSpec;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpSchemaConformance;
import cafe.jeffrey.profile.ProfileInitStages;
import cafe.jeffrey.profile.common.pipeline.PipelineRunOptions;
import cafe.jeffrey.profile.common.pipeline.PipelineRunRegistry;
import cafe.jeffrey.profile.mcp.McpNextToolConformance;
import cafe.jeffrey.shared.common.Json;
import org.springframework.ai.tool.annotation.Tool;
import tools.jackson.databind.JsonNode;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

/**
 * The checks every converted answer gets, on its data path and on each status path: its structured
 * content conforms to the schema the tool advertises, its {@code uiLink} lands on a page the router
 * serves, its text is either the record's own JSON or Markdown ending in the footer rendered from the
 * record, and every next call it hands back names an advertised tool with arguments that tool takes.
 */
public final class StructuredAnswers {

    private static final String UI_LINK = "uiLink";
    private static final String FOLLOW_UP = "followUp";
    private static final String NEXT_TOOLS = "nextTools";
    private static final String GUIDANCE = "guidance";
    private static final String TOOL = "tool";
    private static final String ARGUMENTS = "arguments";

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-01T12:00:00Z"), ZoneOffset.UTC);

    private StructuredAnswers() {
    }

    /** An answer whose text is the record's own JSON. */
    public static JsonNode json(Class<?> tools, String method, McpToolOutcome outcome) {
        McpToolResult result = assertInstanceOf(McpToolResult.class, outcome);
        JsonNode structured = conforming(tools, method, result);
        assertEquals(Json.toString(structured), result.text(), "the text is the record's own JSON");
        return structured;
    }

    /** An answer whose text is Markdown for reading, ending with the footer rendered from the record. */
    public static JsonNode markdown(Class<?> tools, String method, McpToolOutcome outcome) {
        McpToolResult result = assertInstanceOf(McpToolResult.class, outcome);
        JsonNode structured = conforming(tools, method, result);
        MarkdownFooters.assertRenderedFrom(result.text(), structured);
        return structured;
    }

    /**
     * An answer of a tool whose subject has no Microscope page, whose text is the record's own JSON:
     * the same checks, and no {@code uiLink} at all.
     */
    public static JsonNode jsonWithoutPage(Class<?> tools, String method, McpToolOutcome outcome) {
        McpToolResult result = assertInstanceOf(McpToolResult.class, outcome);
        JsonNode structured = conformingWithoutPage(tools, method, result);
        assertEquals(Json.toString(structured), result.text(), "the text is the record's own JSON");
        return structured;
    }

    /** The same, for a tool with no page whose text is Markdown ending in the footer of its next calls. */
    public static JsonNode markdownWithoutPage(Class<?> tools, String method, McpToolOutcome outcome) {
        McpToolResult result = assertInstanceOf(McpToolResult.class, outcome);
        JsonNode structured = conformingWithoutPage(tools, method, result);
        MarkdownFooters.assertRenderedFrom(result.text(), structured);
        return structured;
    }

    private static JsonNode conforming(Class<?> tools, String method, McpToolResult result) {
        JsonNode structured = result.structuredContent();
        assertNotNull(structured, "a converted tool answers with structured content");
        McpSchemaConformance.assertConforms(structured, schemaOf(tools, method));
        UiLinkRoutes.assertResolves(structured.get(UI_LINK).asString());
        McpNextToolConformance.assertFollowable(structured, reachable());
        return structured;
    }

    private static JsonNode conformingWithoutPage(Class<?> tools, String method, McpToolResult result) {
        JsonNode structured = result.structuredContent();
        assertNotNull(structured, "a converted tool answers with structured content");
        McpSchemaConformance.assertConforms(structured, schemaOf(tools, method));
        assertFalse(structured.has(UI_LINK), "a tool with no page carries no uiLink");
        McpNextToolConformance.assertFollowable(structured, reachable());
        return structured;
    }

    /** The output schema the tool advertises, generated from the record its annotation names. */
    public static JsonNode schemaOf(Class<?> tools, String method) {
        Method tool = Arrays.stream(tools.getMethods())
                .filter(candidate -> candidate.getName().equals(method))
                .filter(candidate -> candidate.isAnnotationPresent(Tool.class))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no @Tool " + method + " on " + tools.getSimpleName()));
        McpOutputSchema schema = tool.getAnnotation(McpOutputSchema.class);
        assertNotNull(schema, tools.getSimpleName() + "." + method + " declares no output schema");
        return McpSchemaGenerator.schemaOf(schema.value());
    }

    /**
     * The JSON type the tool's output schema gives one component, found by property names from the
     * root and stepping through array items on the way: {@code "string"} for a component that is never
     * null, {@code ["string","null"]} for one that may be.
     */
    public static JsonNode schemaTypeOf(Class<?> tools, String method, String... path) {
        JsonNode node = schemaOf(tools, method);
        for (String name : path) {
            while (node.path("items").isObject()) {
                node = node.get("items");
            }
            node = node.path("properties").path(name);
            assertNotNull(node, "no component " + name);
        }
        return node.path("type");
    }

    /** The @Tool methods of a family class that declare no output schema; empty once it is converted. */
    public static List<String> unschematised(Class<?> tools) {
        return Arrays.stream(tools.getMethods())
                .filter(method -> method.isAnnotationPresent(Tool.class))
                .filter(method -> !method.isAnnotationPresent(McpOutputSchema.class))
                .map(Method::getName)
                .sorted()
                .toList();
    }

    /** Every tool an answer of the profile-scoped families can route to, as an installation advertises it. */
    public static List<McpToolSpec> reachable() {
        return Reachable.SPECS;
    }

    /** Built once, on first use: the specs are reflection over the tool classes and never change. */
    private static final class Reachable {

        private static final List<McpToolSpec> SPECS = build();

        private static List<McpToolSpec> build() {
            return CatalogueSpecs.of(
                    CatalogueSpecs.profileScoped(TracesMcpTools.class, AdvertisedFamilies.TRACES),
                    CatalogueSpecs.profileScoped(TraceAttributesMcpTools.class, AdvertisedFamilies.TRACES),
                    CatalogueSpecs.profileScoped(HttpMcpTools.class, AdvertisedFamilies.HTTP),
                    CatalogueSpecs.profileScoped(JdbcMcpTools.class, AdvertisedFamilies.JDBC),
                    CatalogueSpecs.profileScoped(GrpcMcpTools.class, AdvertisedFamilies.GRPC),
                    CatalogueSpecs.profileScoped(IoMcpTools.class, AdvertisedFamilies.IO),
                    CatalogueSpecs.profileScoped(BlockingMcpTools.class, AdvertisedFamilies.BLOCKING),
                    CatalogueSpecs.profileScoped(MemoryMcpTools.class, AdvertisedFamilies.MEMORY),
                    CatalogueSpecs.profileScoped(MethodTracingMcpTools.class, AdvertisedFamilies.METHOD_TRACING),
                    CatalogueSpecs.profileScoped(FlamegraphMcpTools.class, AdvertisedFamilies.FLAMEGRAPH),
                    CatalogueSpecs.profileScoped(TimelineMcpTools.class, AdvertisedFamilies.TIMELINE),
                    CatalogueSpecs.profileScoped(JvmMcpTools.class, AdvertisedFamilies.JVM),
                    CatalogueSpecs.profileScoped(ProfileMcpTools.class, AdvertisedFamilies.PROFILES),
                    CatalogueSpecs.profileScoped(HeapDumpMcpTools.class, AdvertisedFamilies.HEAP),
                    CatalogueSpecs.profileScoped(IdeMcpTools.class, AdvertisedFamilies.IDE),
                    CatalogueSpecs.profileScoped(DuckDbMcpTools.class, AdvertisedFamilies.JFR),
                    CatalogueSpecs.profileScoped(EventTypeMcpTools.class, AdvertisedFamilies.JFR),
                    CatalogueSpecs.served(HubsMcpToolsFixture.of(mock(HubsManager.class), mock(ProjectManagerResolver.class),
                            mock(RecordingsManager.class), CLOCK).build(), AdvertisedFamilies.HUBS),
                    CatalogueSpecs.served(HubsArtifactsMcpToolsFixture.of(mock(ProjectManagerResolver.class),
                            mock(RecordingsManager.class), Path.of("artifacts"), Path.of("profiles"),
                            new McpOperationRegistry(CLOCK), CLOCK, AdvertisedFamiliesFixture.EVERY_FAMILY).build(),
                            AdvertisedFamilies.HUBS),
                    CatalogueSpecs.served(RecordingsMcpToolsFixture.of(mock(RecordingsManager.class),
                            new PipelineRunRegistry<>(ProfileInitStages.DEFINITION, PipelineRunOptions.unbounded(), CLOCK),
                            new McpOperationRegistry(CLOCK), CLOCK).build(), AdvertisedFamilies.RECORDINGS),
                    CatalogueSpecs.served(new OperationsMcpTools(new McpOperationRegistry(CLOCK), kind -> true),
                            AdvertisedFamilies.OPERATIONS));
        }
    }

    /** The tools the answer's next calls name, in order. */
    public static List<String> nextTools(JsonNode structured) {
        List<String> tools = new ArrayList<>();
        structured.get(FOLLOW_UP).get(NEXT_TOOLS).forEach(call -> tools.add(call.get(TOOL).asString()));
        return tools;
    }

    /** The arguments of the first next call to {@code tool}. */
    public static JsonNode call(JsonNode structured, String tool) {
        for (JsonNode call : structured.get(FOLLOW_UP).get(NEXT_TOOLS)) {
            if (call.get(TOOL).asString().equals(tool)) {
                return call.get(ARGUMENTS);
            }
        }
        throw new AssertionError("no call to " + tool + " in " + structured.get(FOLLOW_UP));
    }

    /** The guidance lines, joined, for a contains check. */
    public static String guidance(JsonNode structured) {
        return structured.get(FOLLOW_UP).get(GUIDANCE).toString();
    }
}
