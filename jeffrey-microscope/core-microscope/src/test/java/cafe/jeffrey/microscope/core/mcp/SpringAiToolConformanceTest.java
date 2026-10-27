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
package cafe.jeffrey.microscope.core.mcp;

import cafe.jeffrey.microscope.core.mcp.tools.BlockingMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.CompareMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.DuckDbMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.EventTypeMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.FlamegraphMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.GrpcMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.HeapComputeMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.HeapDiffMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.HeapDumpMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.HeapOqlMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.HttpMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.HubsArtifactsMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.HubsMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.IdeMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.IoMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.JdbcMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.JvmMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.McpOperationRegistry;
import cafe.jeffrey.microscope.core.mcp.tools.MemoryMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.MethodTracingMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.OperationsMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.ProfileEvidenceMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.ProfileMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.ProfilesMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.RecordingsMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.TimelineMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.TraceAttributesMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.TracesMcpTools;
import cafe.jeffrey.microscope.mcp.protocol.McpCallContext;
import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpSchemaGenerator;
import cafe.jeffrey.microscope.mcp.protocol.McpToolOutcome;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.mcp.protocol.McpToolSpec;
import cafe.jeffrey.profile.mcp.McpNextTool;
import cafe.jeffrey.profile.mcp.McpTestToolsets;
import cafe.jeffrey.profile.mcp.ProfileScopedToolset;
import cafe.jeffrey.profile.mcp.finding.McpFinding;
import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.support.ToolDefinitions;
import org.springframework.ai.tool.support.ToolUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import tools.jackson.databind.JsonNode;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Holds Jeffrey's hand-rolled tool layer to Spring AI's own reading of the same annotations.
 * <p>
 * Jeffrey does not use Spring AI's MCP server. It borrows only the {@link Tool}/{@link ToolParam}
 * annotations and re-derives everything from them itself — the tool name, the description, the JSON
 * Schema — because it needs a profile-scoped toolset and a prefixed name that Spring AI's own
 * machinery does not produce. The annotations are therefore a contract Jeffrey has copied rather than
 * one it enforces, and a copied contract drifts: the reason this file exists is that it already had.
 * <p>
 * So every {@code @Tool} method the MCP server serves is put through both readings and the two are
 * compared. Spring AI's {@link ToolDefinitions}/{@link ToolUtils} are the oracle, and the assertions
 * below say exactly where Jeffrey is allowed to differ and where it is not. Where they legitimately
 * diverge — the prefixed name, the synthetic {@code profileId}, the envelope's {@code McpCallContext}
 * parameter, the required-by-default rule — the
 * divergence is pinned here in one place instead of being rediscovered per family.
 * <p>
 * This is the whole surface at once: no other test covers every family, and a family added to the
 * tools package without being added here is caught by
 * {@link Coverage#coversEveryToolFamilyOnTheClasspath()}.
 */
class SpringAiToolConformanceTest {

    /**
     * Every {@code @Tool} class reachable over MCP — the families the external endpoint assembles,
     * plus the two the Claude Code endpoint serves directly.
     */
    private static final List<Class<?>> TOOL_CLASSES = List.of(
            ProfileEvidenceMcpTools.class, OperationsMcpTools.class,
            ProfilesMcpTools.class,
            ProfileMcpTools.class,
            EventTypeMcpTools.class,
            DuckDbMcpTools.class,
            FlamegraphMcpTools.class,
            CompareMcpTools.class,
            TracesMcpTools.class,
            TraceAttributesMcpTools.class,
            JvmMcpTools.class,
            HttpMcpTools.class,
            JdbcMcpTools.class,
            GrpcMcpTools.class,
            MethodTracingMcpTools.class,
            IoMcpTools.class,
            BlockingMcpTools.class,
            TimelineMcpTools.class,
            MemoryMcpTools.class,
            HeapDiffMcpTools.class,
            HeapOqlMcpTools.class,
            HeapDumpMcpTools.class,
            HeapComputeMcpTools.class,
            RecordingsMcpTools.class,
            HubsMcpTools.class,
            HubsArtifactsMcpTools.class,
            IdeMcpTools.class);

    /** Where a {@code @Tool} class may live and still be reachable over MCP. */
    private static final List<String> TOOL_PACKAGES = List.of("cafe.jeffrey.microscope.core.mcp.tools");

    private static final String TEST_PREFIX = "test";

    private static final String OBJECT_TYPE = "object";
    private static final String SCHEMA_ITEMS = "items";
    private static final String SCHEMA_ADDITIONAL_PROPERTIES = "additionalProperties";
    private static final String TOOL_SEPARATOR = ":";
    private static final String PROPERTY_SEPARATOR = ".";
    private static final String ITEM_SUFFIX = "[]";
    private static final String VALUE_SUFFIX = "{}";

    /**
     * The record components an output schema may leave open as {@code {"type":"object"}}: the plan's
     * three genuinely polymorphic payloads. A next call's arguments differ per tool, a finding's
     * evidence per source, and an operation's result per kind. They are allowed by the component they
     * come from rather than listed per tool, since after the family batches most payloads carry one;
     * a new open object anywhere else fails {@link Identity#openObjectsAppearOnlyWhereThePayloadIsGenuinelyPolymorphic}.
     */
    private static final Set<SanctionedComponent> SANCTIONED_COMPONENTS = Set.of(
            new SanctionedComponent(McpNextTool.class, "arguments"),
            new SanctionedComponent(McpFinding.class, "evidence"),
            new SanctionedComponent(McpOperationRegistry.Snapshot.class, "result"));

    /** One record component, by the record that declares it and its name. */
    private record SanctionedComponent(Class<?> owner, String name) {
    }

    private static final String SCHEMA_PROPERTIES = "properties";
    private static final String SCHEMA_REQUIRED = "required";
    private static final String SCHEMA_TYPE = "type";
    private static final String SCHEMA_DESCRIPTION = "description";

    /**
     * Added by {@link ProfileScopedToolset} to every schema and consumed by the toolset itself, so it
     * has no counterpart in Spring AI's reading of the method.
     */
    private static final String SYNTHETIC_PROFILE_ID = ProfileScopedToolset.PROFILE_ID_ARGUMENT;

    /** What a tool without an output schema may return: text, or an outcome that may be a question or a task. */
    private static final Set<Class<?>> TEXT_RETURN_TYPES = Set.of(String.class, McpToolOutcome.class);

    /** What a tool with an output schema may return: something that can carry structured data. */
    private static final Set<Class<?>> STRUCTURED_RETURN_TYPES = Set.of(McpToolResult.class, McpToolOutcome.class);

    private static boolean admitsAnObject(JsonNode type) {
        if (type.isString()) {
            return OBJECT_TYPE.equals(type.asString());
        }
        for (JsonNode name : type) {
            if (OBJECT_TYPE.equals(name.asString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * An object schema that lists no properties and constrains no values: what the generator writes for
     * {@code McpJsonObject}, and nowhere else.
     */
    private static void collectOpenObjects(JsonNode schema, String path, Set<String> open) {
        if (!schema.isObject()) {
            return;
        }
        if (admitsAnObject(schema.path(SCHEMA_TYPE))
                && !schema.has(SCHEMA_PROPERTIES) && !schema.has(SCHEMA_ADDITIONAL_PROPERTIES)) {
            open.add(path);
        }
        for (Map.Entry<String, JsonNode> property : schema.path(SCHEMA_PROPERTIES).properties()) {
            String separator = path.endsWith(TOOL_SEPARATOR) ? "" : PROPERTY_SEPARATOR;
            collectOpenObjects(property.getValue(), path + separator + property.getKey(), open);
        }
        collectOpenObjects(schema.path(SCHEMA_ITEMS), path + ITEM_SUFFIX, open);
        collectOpenObjects(schema.path(SCHEMA_ADDITIONAL_PROPERTIES), path + VALUE_SUFFIX, open);
    }

    /**
     * The paths, spelled as {@link #collectOpenObjects} spells them, at which the record a tool declares
     * carries a {@link #SANCTIONED_COMPONENTS sanctioned} component: walked through the record components
     * themselves, so a path is allowed because of the type behind it and not because of how it happens
     * to be named.
     */
    private static void collectSanctioned(Type type, String path, Set<String> found) {
        Class<?> raw = type instanceof ParameterizedType parameterized
                ? (Class<?>) parameterized.getRawType() : (Class<?>) type;
        if (type instanceof ParameterizedType parameterized) {
            Type[] arguments = parameterized.getActualTypeArguments();
            if (Collection.class.isAssignableFrom(raw)) {
                collectSanctioned(arguments[0], path + ITEM_SUFFIX, found);
            } else if (Map.class.isAssignableFrom(raw)) {
                collectSanctioned(arguments[1], path + VALUE_SUFFIX, found);
            }
            return;
        }
        if (!raw.isRecord()) {
            return;
        }
        for (RecordComponent component : raw.getRecordComponents()) {
            String separator = path.endsWith(TOOL_SEPARATOR) ? "" : PROPERTY_SEPARATOR;
            String componentPath = path + separator + component.getName();
            if (SANCTIONED_COMPONENTS.contains(new SanctionedComponent(raw, component.getName()))) {
                found.add(componentPath);
            } else {
                collectSanctioned(component.getGenericType(), componentPath, found);
            }
        }
    }

    /**
     * One {@code @Tool} method, read both ways.
     */
    private record ToolMethod(Class<?> type, Method method, McpToolSpec jeffrey, ToolDefinition springAi) {

        @Override
        public String toString() {
            return type.getSimpleName() + "." + method.getName();
        }

        JsonNode jeffreySchema() {
            return jeffrey.inputSchema();
        }

        JsonNode springAiSchema() {
            return Json.readTree(springAi.inputSchema());
        }

        /**
         * The names of the method's {@link McpCallContext} parameters: arguments to Spring AI, the
         * envelope's own to Jeffrey.
         */
        Set<String> callContextParameters() {
            return Stream.of(method.getParameters())
                    .filter(parameter -> parameter.getType() == McpCallContext.class)
                    .map(Parameter::getName)
                    .collect(Collectors.toSet());
        }

        /** Spring AI's names for the arguments, without the context parameter it reads as one. */
        List<String> springAiArguments(List<String> names) {
            Set<String> context = callContextParameters();
            return names.stream().filter(name -> !context.contains(name)).toList();
        }
    }

    /**
     * Jeffrey's own reading of every tool method, obtained through the real toolset.
     * <p>
     * {@link ProfileScopedToolset} is used for all of them, including the families that are registered
     * un-scoped: it takes a {@code Class} rather than an instance and never resolves a target while
     * only the specs are being read, which is what lets this sweep cover classes whose constructors
     * want a live profile database.
     */
    static Stream<ToolMethod> toolMethods() {
        return toolMethodsOf(TOOL_CLASSES);
    }

    private static Stream<ToolMethod> toolMethodsOf(List<Class<?>> types) {
        List<ToolMethod> methods = new ArrayList<>();
        for (Class<?> type : types) {
            ProfileScopedToolset<?> toolset = McpTestToolsets.unscoped(
                    type, TEST_PREFIX, profileId -> {
                        throw new UnsupportedOperationException("specs need no target");
                    });
            for (McpToolSpec spec : toolset.specs()) {
                Method method = declaredToolMethod(type, spec.name());
                methods.add(new ToolMethod(type, method, spec, ToolDefinitions.from(method)));
            }
        }
        return methods.stream();
    }

    private static Method declaredToolMethod(Class<?> type, String prefixedName) {
        String methodName = prefixedName.substring(TEST_PREFIX.length() + 1);
        return Stream.of(type.getMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .filter(candidate -> candidate.isAnnotationPresent(Tool.class))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No @Tool method " + methodName + " on " + type));
    }

    /**
     * What the model is told the tool is called and what it does. Jeffrey prefixes the name with its
     * family; the rest has to match, because it is the same annotation being read twice.
     */
    @Nested
    class Identity {

        @ParameterizedTest(name = "{0}")
        @MethodSource("cafe.jeffrey.microscope.core.mcp.SpringAiToolConformanceTest#toolMethods")
        void theNameIsSpringAisNameUnderJeffreysPrefix(ToolMethod tool) {
            assertEquals(
                    TEST_PREFIX + "_" + ToolUtils.getToolName(tool.method()),
                    tool.jeffrey().name(),
                    "Jeffrey derives the tool name from the method; Spring AI would honour an explicit "
                            + "@Tool(name=...) that Jeffrey ignores");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("cafe.jeffrey.microscope.core.mcp.SpringAiToolConformanceTest#toolMethods")
        void theDescriptionIsTheOneSpringAiWouldRead(ToolMethod tool) {
            assertEquals(tool.springAi().description(), tool.jeffrey().description());
        }

        /**
         * Tools return readable text or an explicitly declared structured result, rather than
         * relying on an arbitrary object's {@code toString} representation.
         */
        @ParameterizedTest(name = "{0}")
        @MethodSource("cafe.jeffrey.microscope.core.mcp.SpringAiToolConformanceTest#toolMethods")
        void theToolReturnsTextOrADeclaredStructuredResult(ToolMethod tool) {
            Class<?> returned = tool.method().getReturnType();
            if (tool.method().isAnnotationPresent(McpOutputSchema.class)) {
                assertTrue(STRUCTURED_RETURN_TYPES.contains(returned), returned.getName());
                assertNotNull(tool.jeffrey().outputSchema());
            } else {
                assertTrue(TEXT_RETURN_TYPES.contains(returned), returned.getName());
            }
        }

        /**
         * Every advertised output schema is the one {@link McpSchemaGenerator} writes for the declared
         * record: closed, the generator's keywords only, and never a hand-written string beside it.
         */
        @ParameterizedTest(name = "{0}")
        @MethodSource("cafe.jeffrey.microscope.core.mcp.SpringAiToolConformanceTest#toolMethods")
        void theOutputSchemaIsTheGeneratorsReadingOfTheDeclaredRecord(ToolMethod tool) {
            McpOutputSchema declared = tool.method().getAnnotation(McpOutputSchema.class);
            if (declared == null) {
                assertNull(tool.jeffrey().outputSchema());
                return;
            }
            assertEquals(McpSchemaGenerator.schemaOf(declared.value()), tool.jeffrey().outputSchema());
        }

        /**
         * {@code McpJsonObject} is the one open shape an output schema may hold, and only where a payload
         * is genuinely polymorphic. Pinned as a set, so a new open object is a decision somebody makes
         * here rather than a schema that quietly promises nothing.
         */
        @Test
        void openObjectsAppearOnlyWhereThePayloadIsGenuinelyPolymorphic() {
            Set<String> open = new TreeSet<>();
            Set<String> sanctioned = new TreeSet<>();
            toolMethods()
                    .filter(tool -> tool.jeffrey().outputSchema() != null)
                    .forEach(tool -> {
                        collectOpenObjects(tool.jeffrey().outputSchema(), tool + TOOL_SEPARATOR, open);
                        collectSanctioned(tool.method().getAnnotation(McpOutputSchema.class).value(),
                                tool + TOOL_SEPARATOR, sanctioned);
                    });

            assertEquals(sanctioned, open);
        }

        /**
         * The component rule finds each sanctioned component where the converted families carry it,
         * and nothing that is not one of them.
         */
        @Test
        void sanctionedComponentsAreFoundThroughTheRecordComponent() {
            Set<String> sanctioned = new TreeSet<>();
            toolMethods()
                    .filter(tool -> tool.jeffrey().outputSchema() != null)
                    .forEach(tool -> collectSanctioned(tool.method().getAnnotation(McpOutputSchema.class).value(),
                            tool + TOOL_SEPARATOR, sanctioned));

            assertTrue(sanctioned.containsAll(List.of(
                    "ProfileEvidenceMcpTools.evidence:followUp.nextTools[].arguments",
                    "ProfileEvidenceMcpTools.evidence:findings[].evidence",
                    "ProfileEvidenceMcpTools.evidence:findings[].nextTool.arguments",
                    "ProfileMcpTools.summary:topFindings[].evidence",
                    "CompareMcpTools.quality:followUp.nextTools[].arguments",
                    "OperationsMcpTools.status:result",
                    "RecordingsMcpTools.status:operation.result",
                    "RecordingsMcpTools.status:operation.followUp.nextTools[].arguments")),
                    sanctioned.toString());
            assertTrue(sanctioned.stream().allMatch(path -> path.endsWith(".arguments")
                    || path.endsWith(".evidence") || path.endsWith(":result") || path.endsWith(".result")),
                    sanctioned.toString());
        }
    }

    /**
     * The schema is the tool's contract with the model, and the half most likely to drift: Jeffrey
     * generates it by hand where Spring AI generates it from the same annotations.
     */
    @Nested
    class Schema {

        @ParameterizedTest(name = "{0}")
        @MethodSource("cafe.jeffrey.microscope.core.mcp.SpringAiToolConformanceTest#toolMethods")
        void bothReadTheSameArguments(ToolMethod tool) {
            assertSameArguments(tool);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("cafe.jeffrey.microscope.core.mcp.SpringAiToolConformanceTest#toolMethods")
        void bothGiveEachArgumentTheSameType(ToolMethod tool) {
            JsonNode springAi = tool.springAiSchema().get(SCHEMA_PROPERTIES);
            JsonNode jeffrey = tool.jeffreySchema().get(SCHEMA_PROPERTIES);

            for (String name : tool.springAiArguments(propertyNames(tool.springAiSchema()))) {
                assertEquals(
                        springAi.get(name).get(SCHEMA_TYPE).asString(),
                        jeffrey.get(name).get(SCHEMA_TYPE).asString(),
                        "argument '" + name + "' is advertised as two different JSON types");
            }
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("cafe.jeffrey.microscope.core.mcp.SpringAiToolConformanceTest#toolMethods")
        void bothCarryTheSameArgumentDescriptions(ToolMethod tool) {
            JsonNode springAi = tool.springAiSchema().get(SCHEMA_PROPERTIES);
            JsonNode jeffrey = tool.jeffreySchema().get(SCHEMA_PROPERTIES);

            for (String name : tool.springAiArguments(propertyNames(tool.springAiSchema()))) {
                JsonNode expected = springAi.get(name).get(SCHEMA_DESCRIPTION);
                JsonNode actual = jeffrey.get(name).get(SCHEMA_DESCRIPTION);
                assertEquals(
                        expected == null ? null : expected.asString(),
                        actual == null ? null : actual.asString(),
                        "argument '" + name + "' is described differently to the model");
            }
        }
    }

    /**
     * The one place the two readings genuinely disagree, and the rule that keeps the disagreement
     * from mattering.
     * <p>
     * Spring AI treats a parameter as required unless {@code @ToolParam(required = false)} says
     * otherwise — and treats a parameter with no annotation at all as required. Jeffrey inverts that:
     * only an explicit {@code required = true} makes an argument required, on the reasoning that an
     * unannotated parameter carries no contract to inherit one from.
     * <p>
     * Left alone, that would mean the same method advertising two different contracts — Spring AI's
     * in-process tool-calling path demanding an argument the MCP path calls optional. It does not,
     * because of the rule asserted below: every tool parameter states its own requiredness. With that
     * held, the two readings cannot disagree, and the divergence stays theoretical.
     */
    @Nested
    class Requiredness {

        @ParameterizedTest(name = "{0}")
        @MethodSource("cafe.jeffrey.microscope.core.mcp.SpringAiToolConformanceTest#toolMethods")
        void everyArgumentDeclaresWhetherItIsRequired(ToolMethod tool) {
            assertEveryArgumentDeclaresItsRequiredness(tool);
        }

        /**
         * With every argument declaring itself, the two readings must now agree exactly.
         */
        @ParameterizedTest(name = "{0}")
        @MethodSource("cafe.jeffrey.microscope.core.mcp.SpringAiToolConformanceTest#toolMethods")
        void bothRequireTheSameArguments(ToolMethod tool) {
            assertSameRequired(tool);
        }

    }

    /**
     * The second pinned divergence, beside {@code profileId}: a tool that has to know what the client
     * can do declares an {@link McpCallContext} parameter. Spring AI reads it as one more argument (it
     * exempts only its own {@code ToolContext}); Jeffrey binds it from the envelope and leaves it out of
     * the schema. With it set aside, the two readings must agree as they do for every other tool.
     * <p>
     * Held against a fixture as well as the real families, so the rule is proven before the first
     * production tool takes a context.
     */
    @Nested
    class CallContext {

        @Test
        void springAiReadsTheContextAsAnArgumentAndJeffreyDoesNot() {
            ToolMethod tool = toolMethodsOf(List.of(ContextFixture.class)).findFirst().orElseThrow();

            assertTrue(propertyNames(tool.springAiSchema()).contains(CONTEXT_FIXTURE_PARAMETER),
                    "Spring AI no longer reads the context as an argument; the divergence can be dropped");
            assertFalse(propertyNames(tool.jeffreySchema()).contains(CONTEXT_FIXTURE_PARAMETER));
            assertFalse(requiredNames(tool.jeffreySchema()).contains(CONTEXT_FIXTURE_PARAMETER));
        }

        @Test
        void withTheContextSetAsideBothReadingsAgree() {
            ToolMethod tool = toolMethodsOf(List.of(ContextFixture.class)).findFirst().orElseThrow();

            assertSameArguments(tool);
            assertSameRequired(tool);
            assertEveryArgumentDeclaresItsRequiredness(tool);
        }
    }

    /** The parameter name the fixture gives its context, so the divergence has something to point at. */
    private static final String CONTEXT_FIXTURE_PARAMETER = "call";

    public static class ContextFixture {

        @Tool(description = "Answers or asks, depending on the client")
        public McpToolOutcome decide(
                @ToolParam(required = true, description = "what to decide") String question,
                McpCallContext call) {
            return McpToolResult.text(question + ":" + call.canElicitForm());
        }
    }

    /**
     * Spring AI refuses a toolset carrying one name twice, and so does Jeffrey. Asserted against the
     * real families rather than against a fixture, because the failure it prevents — one of two
     * overloads permanently unreachable — is silent.
     */
    @Nested
    class Uniqueness {

        @Test
        void noFamilyCarriesOneToolNameTwice() {
            for (Class<?> type : TOOL_CLASSES) {
                List<String> names = Stream.of(type.getMethods())
                        .filter(method -> method.isAnnotationPresent(Tool.class))
                        .map(ToolUtils::getToolName)
                        .sorted()
                        .toList();

                assertEquals(names.size(), Set.copyOf(names).size(),
                        type.getSimpleName() + " declares one @Tool name more than once");
            }
        }

        // Uniqueness *across* families is not asserted here. It depends on the prefix each family is
        // registered under, which is the assembler's business rather than the class's, and
        // CompositeToolset already refuses a duplicate when the real server is built —
        // McpToolsetAssemblerTest does exactly that.
    }

    /**
     * A family added to the server and not to this file would be silently exempt from everything
     * above, which is the failure mode of every hand-maintained list.
     */
    @Nested
    class Coverage {

        /**
         * Found by scanning rather than read off a second hand-maintained list: the point is to catch
         * a family somebody adds to the tools package and forgets to add here, and a list that has to
         * be updated to notice a missing update would not catch anything.
         */
        @Test
        void coversEveryToolFamilyOnTheClasspath() {
            List<String> onClasspath = scanForToolClasses().stream()
                    .map(Class::getSimpleName)
                    .sorted()
                    .toList();
            List<String> covered = TOOL_CLASSES.stream()
                    .map(Class::getSimpleName)
                    .sorted()
                    .toList();

            assertEquals(onClasspath, covered,
                    "a @Tool family exists that this conformance sweep does not cover");
        }

        @Test
        void everyFamilyContributesAtLeastOneTool() {
            for (Class<?> type : TOOL_CLASSES) {
                long tools = Stream.of(type.getMethods())
                        .filter(method -> method.isAnnotationPresent(Tool.class))
                        .count();
                assertTrue(tools > 0, type.getSimpleName() + " advertises no tools at all");
            }
        }

        /**
         * A floor rather than a count: the point is that the enumeration is still finding the surface,
         * and a test that had to be edited every time a tool was added would be edited without being
         * read.
         */
        @Test
        void theSweepIsNotVacuous() {
            assertTrue(toolMethods().count() >= 80,
                    "the MCP server advertises around a hundred tools; a much smaller sweep means the "
                            + "enumeration silently stopped finding them");
        }
    }

    /**
     * Every class carrying a {@code @Tool} method in the packages the MCP families live in.
     */
    private static List<Class<?>> scanForToolClasses() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*")));

        List<Class<?>> found = new ArrayList<>();
        for (String basePackage : TOOL_PACKAGES) {
            for (BeanDefinition candidate : scanner.findCandidateComponents(basePackage)) {
                Class<?> type = resolve(candidate.getBeanClassName());
                boolean declaresTools = Stream.of(type.getMethods())
                        .anyMatch(method -> method.isAnnotationPresent(Tool.class));
                if (declaresTools) {
                    found.add(type);
                }
            }
        }
        return found;
    }

    private static Class<?> resolve(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Scanned a class that cannot be loaded: " + className, e);
        }
    }

    private static void assertSameArguments(ToolMethod tool) {
        assertEquals(
                tool.springAiArguments(propertyNames(tool.springAiSchema())),
                withoutSyntheticArgument(propertyNames(tool.jeffreySchema())),
                "the two readings disagree about which arguments the tool takes");
    }

    private static void assertSameRequired(ToolMethod tool) {
        assertEquals(
                tool.springAiArguments(requiredNames(tool.springAiSchema())),
                withoutSyntheticArgument(requiredNames(tool.jeffreySchema())),
                "the two readings disagree about which arguments a caller must supply");
    }

    private static void assertEveryArgumentDeclaresItsRequiredness(ToolMethod tool) {
        for (Parameter parameter : tool.method().getParameters()) {
            if (tool.callContextParameters().contains(parameter.getName())) {
                continue;
            }
            ToolParam declared = parameter.getAnnotation(ToolParam.class);
            assertTrue(declared != null,
                    "argument '" + parameter.getName() + "' carries no @ToolParam, so Spring AI "
                            + "reads it as required and Jeffrey reads it as optional");
        }
    }

    private static List<String> propertyNames(JsonNode schema) {
        JsonNode properties = schema.get(SCHEMA_PROPERTIES);
        if (properties == null) {
            return List.of();
        }
        return properties.propertyStream()
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    private static List<String> requiredNames(JsonNode schema) {
        JsonNode required = schema.get(SCHEMA_REQUIRED);
        if (required == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (JsonNode entry : required) {
            names.add(entry.asString());
        }
        names.sort(Comparator.naturalOrder());
        return names;
    }

    private static List<String> withoutSyntheticArgument(List<String> names) {
        return names.stream().filter(name -> !SYNTHETIC_PROFILE_ID.equals(name)).toList();
    }
}
