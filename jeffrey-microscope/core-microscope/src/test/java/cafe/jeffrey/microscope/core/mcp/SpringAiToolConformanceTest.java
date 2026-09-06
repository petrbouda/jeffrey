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
package cafe.jeffrey.microscope.core.mcp;

import cafe.jeffrey.microscope.core.mcp.tools.BlockingMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.CompareMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.EventTypeMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.FlamegraphMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.GrpcMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.HeapComputeMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.HeapDiffMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.HeapOqlMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.HttpMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.HubsMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.IdeMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.IoMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.JdbcMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.JvmMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.MemoryMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.MethodTracingMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.ProfileMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.ProfilesMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.RecordingsMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.TimelineMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.TraceAttributesMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.TracesMcpTools;
import cafe.jeffrey.profile.ai.duckdb.heapdump.tools.HeapDumpMcpTools;
import cafe.jeffrey.profile.ai.duckdb.jfr.tools.DuckDbMcpTools;
import cafe.jeffrey.profile.mcp.McpToolSpec;
import cafe.jeffrey.profile.mcp.ProfileScopedToolset;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * diverge — the prefixed name, the synthetic {@code profileId}, the required-by-default rule — the
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
            IdeMcpTools.class);

    /** Where a {@code @Tool} class may live and still be reachable over MCP. */
    private static final List<String> TOOL_PACKAGES = List.of(
            "cafe.jeffrey.microscope.core.mcp.tools",
            "cafe.jeffrey.profile.ai.duckdb.jfr.tools",
            "cafe.jeffrey.profile.ai.duckdb.heapdump.tools");

    private static final String TEST_PREFIX = "test";

    private static final String SCHEMA_PROPERTIES = "properties";
    private static final String SCHEMA_REQUIRED = "required";
    private static final String SCHEMA_TYPE = "type";
    private static final String SCHEMA_DESCRIPTION = "description";

    /**
     * Added by {@link ProfileScopedToolset} to every schema and consumed by the toolset itself, so it
     * has no counterpart in Spring AI's reading of the method.
     */
    private static final String SYNTHETIC_PROFILE_ID = ProfileScopedToolset.PROFILE_ID_ARGUMENT;

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
        List<ToolMethod> methods = new ArrayList<>();
        for (Class<?> type : TOOL_CLASSES) {
            ProfileScopedToolset<?> toolset = new ProfileScopedToolset<>(
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
         * Jeffrey renders a result straight into the MCP content block, so a tool that returned
         * something else would reach the model as whatever {@code toString} happened to produce.
         */
        @ParameterizedTest(name = "{0}")
        @MethodSource("cafe.jeffrey.microscope.core.mcp.SpringAiToolConformanceTest#toolMethods")
        void theToolReturnsAString(ToolMethod tool) {
            assertEquals(String.class, tool.method().getReturnType());
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
            assertEquals(
                    propertyNames(tool.springAiSchema()),
                    withoutSyntheticArgument(propertyNames(tool.jeffreySchema())),
                    "the two readings disagree about which arguments the tool takes");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("cafe.jeffrey.microscope.core.mcp.SpringAiToolConformanceTest#toolMethods")
        void bothGiveEachArgumentTheSameType(ToolMethod tool) {
            JsonNode springAi = tool.springAiSchema().get(SCHEMA_PROPERTIES);
            JsonNode jeffrey = tool.jeffreySchema().get(SCHEMA_PROPERTIES);

            for (String name : propertyNames(tool.springAiSchema())) {
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

            for (String name : propertyNames(tool.springAiSchema())) {
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
            for (Parameter parameter : tool.method().getParameters()) {
                ToolParam declared = parameter.getAnnotation(ToolParam.class);
                assertTrue(declared != null,
                        "argument '" + parameter.getName() + "' carries no @ToolParam, so Spring AI "
                                + "reads it as required and Jeffrey reads it as optional");
            }
        }

        /**
         * With every argument declaring itself, the two readings must now agree exactly.
         */
        @ParameterizedTest(name = "{0}")
        @MethodSource("cafe.jeffrey.microscope.core.mcp.SpringAiToolConformanceTest#toolMethods")
        void bothRequireTheSameArguments(ToolMethod tool) {
            assertEquals(
                    requiredNames(tool.springAiSchema()),
                    withoutSyntheticArgument(requiredNames(tool.jeffreySchema())),
                    "the two readings disagree about which arguments a caller must supply");
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
