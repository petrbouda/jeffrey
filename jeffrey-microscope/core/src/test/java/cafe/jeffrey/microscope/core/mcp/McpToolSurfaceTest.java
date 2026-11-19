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

import cafe.jeffrey.microscope.mcp.protocol.McpToolSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The conventions Tier 3 made of the whole tool surface, checked on every advertised tool rather than
 * family by family: structured answers wherever a tool answers with a record, UTC epoch-millisecond
 * windows and cursors instead of recording offsets and skip counts, heap object ids as strings, one
 * spelling per duration unit, and enum values an agent can pass straight back.
 */
class McpToolSurfaceTest {

    /**
     * The tools that answer with text alone, and why: their answer is a document to read, with no
     * record behind it for a schema to describe.
     */
    private static final Map<String, String> TEXT_ONLY = Map.of(
            "ide_source", "The answer is one class's source text as the IDE holds it; there is no record, and "
                    + "a JSON string would only escape the lines an agent reads.");

    /** Inputs that no tool takes any more: offsets from the recording start, a skip count, the old window names. */
    private static final Set<String> RETIRED_INPUTS = Set.of("startMs", "endMs", "offset", "startTime", "endTime");

    private static final String OBJECT_ID = "objectId";
    private static final String MILLIS_SUFFIX = "Millis";
    private static final String STRING_TYPE = "string";
    private static final Pattern UPPER_SNAKE = Pattern.compile("[A-Z][A-Z0-9_]*");

    /**
     * Enum inputs whose values are not constant names, and why: {@code profiles_viewLink.view} takes the
     * route paths the router serves, which are what the link it answers with echoes, so they round-trip
     * as they are.
     */
    private static final Set<String> ROUTE_VALUED_INPUTS = Set.of("profiles_viewLink.view");

    /**
     * Output fields still spelled {@code …Millis}: components of shared domain records the REST API and
     * the web UI read under the same names, published through MCP as they are.
     */
    private static final Set<String> DOMAIN_MILLIS_FIELDS = Set.of();

    private static List<McpToolSpec> specs;

    @BeforeAll
    static void advertise() {
        specs = AdvertisedTools.all();
    }

    @Nested
    class RecordAnswers {

        @Test
        void everyToolWithARecordDeclaresAnOutputSchema() {
            List<String> missing = specs.stream()
                    .filter(spec -> spec.outputSchema() == null)
                    .map(McpToolSpec::name)
                    .filter(name -> !TEXT_ONLY.containsKey(name))
                    .sorted()
                    .toList();

            assertEquals(List.of(), missing);
        }

        @Test
        void aTextOnlyToolIsAdvertisedAndReallyHasNoSchema() {
            for (Map.Entry<String, String> entry : TEXT_ONLY.entrySet()) {
                McpToolSpec spec = specs.stream().filter(candidate -> candidate.name().equals(entry.getKey()))
                        .findFirst().orElseThrow(() -> new AssertionError(entry.getKey() + " is not advertised"));
                assertEquals(null, spec.outputSchema(), spec.name());
                assertTrue(!entry.getValue().isBlank(), spec.name());
            }
        }

        @Test
        void noAnswerCarriesProseNextSteps() {
            List<String> prose = new ArrayList<>();
            eachOutputProperty((tool, name) -> {
                if (name.equals("nextSteps") || name.equals("nextStep")) {
                    prose.add(tool + "." + name);
                }
            });

            assertEquals(List.of(), prose);
        }
    }

    @Nested
    class Inputs {

        @Test
        void noToolTakesARecordingOffsetOrASkipCount() {
            List<String> retired = new ArrayList<>();
            for (McpToolSpec spec : specs) {
                spec.inputSchema().path("properties").propertyNames().forEach(name -> {
                    if (RETIRED_INPUTS.contains(name)) {
                        retired.add(spec.name() + "." + name);
                    }
                });
            }

            assertEquals(List.of(), retired);
        }

        @Test
        void everyObjectIdInputIsAString() {
            List<String> numeric = new ArrayList<>();
            for (McpToolSpec spec : specs) {
                JsonNode objectId = spec.inputSchema().path("properties").path(OBJECT_ID);
                if (!objectId.isMissingNode() && !STRING_TYPE.equals(objectId.path("type").asString())) {
                    numeric.add(spec.name());
                }
            }

            assertEquals(List.of(), numeric);
        }

        @Test
        void everyEnumInputIsItsUpperSnakeConstantName() {
            List<String> lower = new ArrayList<>();
            for (McpToolSpec spec : specs) {
                spec.inputSchema().path("properties").properties().stream()
                        .filter(property -> !ROUTE_VALUED_INPUTS.contains(spec.name() + "." + property.getKey()))
                        .forEach(property -> enumValues(property.getValue()).stream()
                                .filter(value -> !UPPER_SNAKE.matcher(value).matches())
                                .forEach(value -> lower.add(spec.name() + "." + property.getKey() + "=" + value)));
            }

            assertEquals(List.of(), lower);
        }
    }

    @Nested
    class Outputs {

        @Test
        void everyObjectIdOutputIsAString() {
            List<String> numeric = new ArrayList<>();
            for (McpToolSpec spec : specs) {
                walk(spec.name(), spec.outputSchema(), (path, node) -> {
                    if (path.endsWith("." + OBJECT_ID) && !typeNames(node).contains(STRING_TYPE)) {
                        numeric.add(path);
                    }
                });
            }

            assertEquals(List.of(), numeric);
        }

        @Test
        void aDurationIsSpelledMsOrNanosNeverMillis() {
            Set<String> millis = new TreeSet<>();
            eachOutputProperty((tool, name) -> {
                if (name.endsWith(MILLIS_SUFFIX) && !DOMAIN_MILLIS_FIELDS.contains(name)) {
                    millis.add(tool + "." + name);
                }
            });

            assertEquals(Set.of(), millis);
        }

        @Test
        void everyEnumOutputIsItsUpperSnakeConstantName() {
            Set<String> lower = new TreeSet<>();
            for (McpToolSpec spec : specs) {
                walk(spec.name(), spec.outputSchema(), (path, node) -> enumValues(node).stream()
                        .filter(value -> !UPPER_SNAKE.matcher(value).matches())
                        .forEach(value -> lower.add(path + "=" + value)));
            }

            assertEquals(Set.of(), lower);
        }
    }

    private static void eachOutputProperty(BiConsumer<String, String> visitor) {
        for (McpToolSpec spec : specs) {
            walk(spec.name(), spec.outputSchema(), (path, node) ->
                    visitor.accept(spec.name(), path.substring(path.lastIndexOf('.') + 1)));
        }
    }

    /** Every named property of a schema, at any depth, with its dotted path from the tool. */
    private static void walk(String path, JsonNode schema, BiConsumer<String, JsonNode> visitor) {
        if (schema == null || !schema.isObject()) {
            return;
        }
        schema.path("properties").properties().forEach(property -> {
            String child = path + "." + property.getKey();
            visitor.accept(child, property.getValue());
            walk(child, property.getValue(), visitor);
        });
        walk(path, schema.path("items"), visitor);
        walk(path, schema.path("additionalProperties"), visitor);
    }

    private static List<String> enumValues(JsonNode node) {
        List<String> values = new ArrayList<>();
        node.path("enum").forEach(value -> {
            if (value.isString()) {
                values.add(value.asString());
            }
        });
        node.path("items").path("enum").forEach(value -> {
            if (value.isString()) {
                values.add(value.asString());
            }
        });
        return values;
    }

    private static Set<String> typeNames(JsonNode node) {
        Set<String> names = new TreeSet<>();
        JsonNode type = node.path("type");
        if (type.isString()) {
            names.add(type.asString());
        }
        type.forEach(each -> names.add(each.asString()));
        return names;
    }
}
