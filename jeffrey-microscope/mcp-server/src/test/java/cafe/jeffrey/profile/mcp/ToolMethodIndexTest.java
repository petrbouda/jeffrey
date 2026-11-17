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
package cafe.jeffrey.profile.mcp;

import cafe.jeffrey.microscope.mcp.protocol.CompositeToolset;
import cafe.jeffrey.microscope.mcp.protocol.McpCallContext;
import cafe.jeffrey.microscope.mcp.protocol.McpClientCapabilities;
import cafe.jeffrey.microscope.mcp.protocol.McpFormElicitation;
import cafe.jeffrey.microscope.mcp.protocol.McpFormSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpSchemaGenerator;
import cafe.jeffrey.microscope.mcp.protocol.McpToolOutcome;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.mcp.protocol.McpToolSpec;
import cafe.jeffrey.microscope.mcp.protocol.ToolDispatchException;
import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The contract a tool family is held to when it is indexed, and what a caller can rely on afterwards.
 */
class ToolMethodIndexTest {

    /**
     * Everything a family does wrong is refused while it is being assembled, not when a model happens
     * to call the offending tool — which on an MCP server means during somebody's session, with a
     * message that names neither the tool nor the mistake.
     */
    @Nested
    class Rejections {

        @Test
        void refusesTwoToolMethodsThatWouldShareOneName() {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> new ReflectiveToolset(new OverloadedTools(), "test"));
            assertTrue(e.getMessage().contains("test_query"));
            assertTrue(e.getMessage().contains("overloaded"));
        }

        @Test
        void refusesAParameterTypeThatCannotCrossJson() {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> new ReflectiveToolset(new UnsupportedParamTools(), "test"));
            assertTrue(e.getMessage().contains("window"));
            assertTrue(e.getMessage().contains("java.time.Duration"));
        }

        /**
         * A default the bounds exclude is a contract that contradicts itself; the model would be told
         * to omit the argument and then that the value it stands for is out of range.
         */
        @Test
        void refusesADefaultOutsideItsBounds() {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> new ReflectiveToolset(new DefaultOutOfBoundsTools(), "test"));
            assertTrue(e.getMessage().contains("limit"), e.getMessage());
        }

        @Test
        void refusesAMinimumAboveTheMaximum() {
            assertThrows(IllegalStateException.class,
                    () -> new ReflectiveToolset(new InvertedBoundsTools(), "test"));
        }

        @Test
        void refusesBoundsOnAParameterThatIsNotANumber() {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> new ReflectiveToolset(new BoundedStringTools(), "test"));
            assertTrue(e.getMessage().contains("name"), e.getMessage());
        }

        /** An exclusive bound with no bound to be exclusive about says nothing a client can act on. */
        @Test
        void refusesAnExclusiveBoundThatIsNotDeclared() {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> new ReflectiveToolset(new ExclusiveWithoutBoundTools(), "test"));
            assertTrue(e.getMessage().contains("pct"), e.getMessage());
        }

        @Test
        void refusesADefaultOnAnExclusiveBound() {
            assertThrows(IllegalStateException.class,
                    () -> new ReflectiveToolset(new DefaultOnExclusiveBoundTools(), "test"));
        }

        @Test
        void refusesAnEmptyExclusiveRange() {
            assertThrows(IllegalStateException.class,
                    () -> new ReflectiveToolset(new EmptyExclusiveRangeTools(), "test"));
        }

        @Test
        void refusesAFractionalBoundOnAnIntegerParameter() {
            assertThrows(IllegalStateException.class,
                    () -> new ReflectiveToolset(new FractionalIntegerBoundsTools(), "test"));
        }

        @Test
        void refusesAListOfAnythingButStrings() {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> new ReflectiveToolset(new IntegerListTools(), "test"));
            assertTrue(e.getMessage().contains("ids"), e.getMessage());
        }
    }

    /**
     * The name and description come from Spring AI's own reading of the annotation, so an explicit
     * name is honoured rather than silently replaced by the method's. Jeffrey contributes the family
     * prefix and nothing else.
     */
    @Nested
    class Naming {

        @Test
        void honoursAnExplicitToolName() {
            ReflectiveToolset toolset = new ReflectiveToolset(new NamedTools(), "test");

            assertEquals(
                    List.of("test_run_query"),
                    toolset.specs().stream().map(McpToolSpec::name).toList());
        }

        @Test
        void dispatchesOnTheDeclaredNameRatherThanTheMethodName() {
            ReflectiveToolset toolset = new ReflectiveToolset(new NamedTools(), "test");

            assertEquals("ran", toolset.call("test_run_query", Json.createObject()));
            assertThrows(IllegalArgumentException.class,
                    () -> toolset.call("test_execute", Json.createObject()));
        }

        @Test
        void fallsBackToTheMethodNameWhenNoneIsDeclared() {
            ReflectiveToolset toolset = new ReflectiveToolset(new NumericTools(), "test");

            assertTrue(toolset.specs().stream()
                    .anyMatch(spec -> spec.name().equals("test_alpha")));
        }
    }

    @Nested
    class Schema {

        private final ReflectiveToolset toolset = new ReflectiveToolset(new NumericTools(), "test");

        /**
         * {@code Class#getMethods} promises no order at all, so an unsorted index could hand two runs
         * of the same build a differently ordered {@code tools/list} — and the order is what a model
         * reads first.
         */
        @Test
        void listsToolsInAStableOrder() {
            assertEquals(
                    List.of("test_alpha", "test_beta", "test_gamma"),
                    toolset.specs().stream().map(McpToolSpec::name).toList());
        }

        /**
         * The schema's type and the binding come from one table, so a parameter cannot be advertised
         * as one thing and read as another.
         */
        @Test
        void advertisesAFloatingPointParameterAsANumber() {
            ObjectNode properties = (ObjectNode) specOf("test_beta").inputSchema().get("properties");
            assertEquals("number", properties.get("ratio").get("type").asString());
        }

        /**
         * A misspelt argument is not silently ignored by the server, so the schema says so too:
         * clients that validate before sending catch the mistake without a round trip.
         */
        @Test
        void closesTheArgumentObject() {
            JsonNode schema = specOf("test_beta").inputSchema();
            assertFalse(schema.get("additionalProperties").asBoolean(true));
        }

        private McpToolSpec specOf(String name) {
            return toolset.specs().stream()
                    .filter(spec -> spec.name().equals(name))
                    .findFirst()
                    .orElseThrow();
        }
    }

    @Nested
    class Binding {

        private final ReflectiveToolset toolset = new ReflectiveToolset(new NumericTools(), "test");

        @Test
        void bindsAFloatingPointArgumentRatherThanFailingInsideTheCall() {
            assertEquals("0.25", toolset.call("test_beta", Json.createObject().put("ratio", 0.25)));
        }

        /**
         * A primitive cannot hold null, so an omitted one takes its own zero; a boxed one takes null,
         * which is what lets a tool tell "not given" from "given as zero".
         */
        @Test
        void distinguishesAnOmittedBoxedArgumentFromAnOmittedPrimitive() {
            assertEquals("0.0/null", toolset.call("test_gamma", Json.createObject()));
        }
    }

    @Nested
    class Bounds {

        private final ReflectiveToolset toolset = new ReflectiveToolset(new BoundedTools(), "test");

        @Test
        void advertisesTheDefaultAndTheRangeOfAnIntegerAsIntegers() {
            JsonNode limit = propertyOf("test_page", "limit");

            assertTrue(limit.get("default").isIntegralNumber(), limit.toString());
            assertEquals(20, limit.get("default").asInt());
            assertEquals(1, limit.get("minimum").asInt());
            assertEquals(50, limit.get("maximum").asInt());
            assertTrue(limit.get("maximum").isIntegralNumber(), limit.toString());
        }

        @Test
        void advertisesOnlyTheBoundsThatAreDeclared() {
            JsonNode offset = propertyOf("test_page", "offset");

            assertEquals(0, offset.get("default").asInt());
            assertEquals(0, offset.get("minimum").asInt());
            assertFalse(offset.has("maximum"), offset.toString());
        }

        @Test
        void advertisesTheRangeOfAFloatingPointNumber() {
            JsonNode threshold = propertyOf("test_threshold", "pct");

            assertEquals(0.0, threshold.get("minimum").asDouble());
            assertEquals(100.0, threshold.get("maximum").asDouble());
            assertFalse(threshold.has("default"), threshold.toString());
        }

        /**
         * A range the tool refuses at its ends is advertised as JSON Schema 2020-12 spells it: the
         * bound as the number under {@code exclusiveMinimum}/{@code exclusiveMaximum}, and no inclusive
         * keyword beside it that would tell the client the end is allowed.
         */
        @Test
        void advertisesAnExclusiveRangeAsNumbers() {
            JsonNode threshold = propertyOf("test_open", "pct");

            assertEquals(0.0, threshold.get("exclusiveMinimum").asDouble());
            assertEquals(100.0, threshold.get("exclusiveMaximum").asDouble());
            assertTrue(threshold.get("exclusiveMinimum").isNumber(), threshold.toString());
            assertFalse(threshold.has("minimum"), threshold.toString());
            assertFalse(threshold.has("maximum"), threshold.toString());
        }

        @Test
        void mixesAnExclusiveAndAnInclusiveBound() {
            JsonNode share = propertyOf("test_open", "share");

            assertEquals(0.0, share.get("exclusiveMinimum").asDouble());
            assertEquals(1.0, share.get("maximum").asDouble());
            assertFalse(share.has("minimum"), share.toString());
            assertFalse(share.has("exclusiveMaximum"), share.toString());
        }

        /**
         * The bounds are advice to the client, not a gate: the tool clamps, so a value outside them
         * still reaches it and is answered with the nearest page rather than refused.
         */
        @Test
        void leavesClampingToTheTool() {
            assertEquals("500/0", toolset.call("test_page", Json.createObject().put("limit", 500).put("offset", 0)));
        }

        private JsonNode propertyOf(String tool, String name) {
            return toolset.specs().stream()
                    .filter(spec -> spec.name().equals(tool))
                    .findFirst()
                    .orElseThrow()
                    .inputSchema()
                    .get("properties")
                    .get(name);
        }
    }

    /**
     * An argument the tool does not take is almost always a misspelt one it does take. Ignored, the
     * call succeeds with the default in its place and the model never learns its limit was dropped.
     */
    @Nested
    class UnknownArguments {

        @Test
        void refusesAnArgumentTheToolDoesNotTakeAndNamesTheOnesItDoes() {
            ReflectiveToolset toolset = new ReflectiveToolset(new BoundedTools(), "test");

            ToolDispatchException e = assertThrows(ToolDispatchException.class,
                    () -> toolset.call("test_page", Json.createObject().put("limt", 5)));

            assertEquals("Unknown argument 'limt'; this tool accepts: limit, offset", e.getMessage());
        }

        @Test
        void saysSoWhenTheToolTakesNoArgumentsAtAll() {
            ReflectiveToolset toolset = new ReflectiveToolset(new NumericTools(), "test");

            ToolDispatchException e = assertThrows(ToolDispatchException.class,
                    () -> toolset.call("test_alpha", Json.createObject().put("verbose", true)));

            assertTrue(e.getMessage().startsWith("Unknown argument 'verbose'"), e.getMessage());
            assertTrue(e.getMessage().contains("no arguments"), e.getMessage());
        }

        /**
         * The profile id is the toolset's own argument rather than the method's; it has been read
         * by the time the method's arguments are bound, and it is one of the arguments the tool takes.
         */
        @Test
        void acceptsTheProfileIdOfAProfileScopedTool() {
            ProfileScopedToolset<BoundedTools> toolset =
                    McpTestToolsets.unscoped(BoundedTools.class, "test", profileId -> new BoundedTools());

            assertEquals("7/0", toolset.call("test_page",
                    Json.createObject().put("profileId", "p-1").put("limit", 7).put("offset", 0)));

            ToolDispatchException e = assertThrows(ToolDispatchException.class,
                    () -> toolset.call("test_page", Json.createObject().put("profileId", "p-1").put("ofset", 1)));
            assertEquals("Unknown argument 'ofset'; this tool accepts: profileId, limit, offset", e.getMessage());
        }
    }

    @Nested
    class Lists {

        private final ReflectiveToolset toolset = new ReflectiveToolset(new ListTools(), "test");

        @Test
        void advertisesAListOfStringsAsAnArrayOfStrings() {
            JsonNode ids = toolset.specs().getFirst().inputSchema().get("properties").get("ids");

            assertEquals("array", ids.get("type").asString());
            assertEquals("string", ids.get("items").get("type").asString());
        }

        @Test
        void bindsAJsonArray() {
            ObjectNode arguments = Json.createObject();
            arguments.putArray("ids").add("a").add("b");

            assertEquals("[a, b]", toolset.call("test_join", arguments));
        }

        /** Clients that learned the parameter as a comma-joined string keep working. */
        @Test
        void bindsACommaSeparatedString() {
            assertEquals("[a, b]", toolset.call("test_join", Json.createObject().put("ids", " a, ,b ")));
        }

        @Test
        void readsAnEmptyListAsOmitted() {
            ObjectNode arguments = Json.createObject();
            arguments.putArray("ids");

            assertEquals("null", toolset.call("test_join", arguments));
            assertEquals("null", toolset.call("test_join", Json.createObject().put("ids", " ")));
        }

        @Test
        void refusesAnArrayHoldingSomethingOtherThanStrings() {
            ObjectNode arguments = Json.createObject();
            arguments.putArray("ids").add("a").add(7);

            ToolDispatchException e = assertThrows(ToolDispatchException.class,
                    () -> toolset.call("test_join", arguments));
            assertTrue(e.getMessage().contains("ids"), e.getMessage());
        }
    }

    /**
     * A tool that has to know what the client can do — ask the user, take a task instead of a wait —
     * declares one {@link McpCallContext} parameter. It is the envelope's to fill, never the model's:
     * absent from the schema, never required, and not an argument a caller can name.
     */
    @Nested
    class CallContext {

        private static final McpCallContext ELICITING_TASK_CLIENT = new McpCallContext(
                McpClientCapabilities.parse(Json.readTree(
                        "{\"elicitation\":{},\"extensions\":{\"io.modelcontextprotocol/tasks\":{}}}")),
                Map.of(), Optional.empty());

        private final ReflectiveToolset toolset = new ReflectiveToolset(new ContextTools(), "test");

        @Test
        void leavesTheContextOutOfTheSchema() {
            JsonNode schema = specOf("test_describe").inputSchema();

            assertEquals(List.of("label"), schema.get("properties").propertyStream().map(Map.Entry::getKey).toList());
            assertFalse(schema.has("required"), schema.toString());
        }

        @Test
        void handsTheToolTheContextItWasCalledWith() {
            McpToolOutcome outcome = toolset.call(
                    "test_describe", Json.createObject().put("label", "x"), ELICITING_TASK_CLIENT);

            assertEquals("x:true/true", outcome.requireComplete().text());
        }

        /** The two-argument call is the text path every existing caller uses: nothing to ask, no task. */
        @Test
        void handsTheToolTheResourceReadContextWhenCalledWithoutOne() {
            assertEquals("x:false/false", toolset.call("test_describe", Json.createObject().put("label", "x")));
        }

        @Test
        void refusesAnArgumentNamedLikeTheContextAndNeverListsIt() {
            ToolDispatchException e = assertThrows(ToolDispatchException.class,
                    () -> toolset.call("test_describe", Json.createObject().put("call", "{}")));

            assertEquals("Unknown argument 'call'; this tool accepts: label", e.getMessage());
        }

        @Test
        void passesAToolsQuestionThroughUnchanged() {
            McpToolOutcome outcome = toolset.call("test_confirm", Json.createObject(), ELICITING_TASK_CLIENT);

            McpToolOutcome.InputRequired asking = assertInstanceOf(McpToolOutcome.InputRequired.class, outcome);
            assertEquals(List.of("confirm"), List.copyOf(asking.requests().keySet()));
        }

        @Test
        void answersWithAResultWhenTheToolDecidesNotToAsk() {
            McpToolOutcome outcome = toolset.call("test_confirm", Json.createObject(), McpCallContext.RESOURCE_READ);

            assertEquals("confirmed", outcome.requireComplete().text());
        }

        /** The resource path cannot carry a question, so it is refused rather than dropped. */
        @Test
        void refusesAQuestionOnThePathThatCannotCarryOne() {
            ReflectiveToolset alwaysAsks = new ReflectiveToolset(new AlwaysAsksTools(), "test");

            assertThrows(IllegalStateException.class, () -> alwaysAsks.callResult("test_ask", Json.createObject()));
        }

        @Test
        void reachesAProfileScopedToolAlongsideTheProfileId() {
            ProfileScopedToolset<ContextTools> scoped =
                    McpTestToolsets.unscoped(ContextTools.class, "test", profileId -> new ContextTools());

            McpToolOutcome outcome = scoped.call("test_describe",
                    Json.createObject().put("profileId", "p-1").put("label", "y"), ELICITING_TASK_CLIENT);

            assertEquals("y:true/true", outcome.requireComplete().text());
            McpToolSpec describe = scoped.specs().stream()
                    .filter(spec -> spec.name().equals("test_describe"))
                    .findFirst()
                    .orElseThrow();
            assertEquals(List.of("profileId", "label"), describe.inputSchema()
                    .get("properties").propertyStream().map(Map.Entry::getKey).toList());
        }

        @Test
        void reachesAToolThroughAComposite() {
            CompositeToolset composite = new CompositeToolset(List.of(toolset));

            McpToolOutcome outcome = composite.call(
                    "test_describe", Json.createObject().put("label", "z"), ELICITING_TASK_CLIENT);

            assertEquals("z:true/true", outcome.requireComplete().text());
        }

        @Test
        void refusesTwoContextParameters() {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> new ReflectiveToolset(new TwoContextTools(), "test"));

            assertTrue(e.getMessage().contains("McpCallContext"), e.getMessage());
        }

        /** Text, a result, or an outcome: anything else would reach the model as its toString. */
        @Test
        void refusesAReturnTypeOutsideTheThree() {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> new ReflectiveToolset(new IntegerReturningTools(), "test"));

            assertTrue(e.getMessage().contains("count"), e.getMessage());
        }

        @Test
        void acceptsAnOutputSchemaOnAToolThatAnswersWithAnOutcome() {
            ReflectiveToolset structured = new ReflectiveToolset(new StructuredOutcomeTools(), "test");

            assertEquals("object", structured.specs().getFirst().outputSchema().get("type").asString());
        }

        /** Generated once, when the family is indexed, and the same object-shaped contract every time. */
        @Test
        void generatesTheOutputSchemaFromTheDeclaredRecord() {
            ReflectiveToolset structured = new ReflectiveToolset(new StructuredOutcomeTools(), "test");

            assertEquals(McpSchemaGenerator.schemaOf(Numbered.class), structured.specs().getFirst().outputSchema());
        }

        @Test
        void refusesAnOutputRecordTheGeneratorCannotDescribeNamingTheToolAndTheComponent() {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> new ReflectiveToolset(new InstantOutputTools(), "test"));

            assertTrue(e.getMessage().contains("InstantOutputTools.run"), e.getMessage());
            assertTrue(e.getMessage().contains("Stamped.at: java.time.Instant is not allowed"), e.getMessage());
        }

        private McpToolSpec specOf(String name) {
            return toolset.specs().stream()
                    .filter(spec -> spec.name().equals(name))
                    .findFirst()
                    .orElseThrow();
        }
    }

    static class BoundedTools {

        @Tool(description = "A page")
        public String page(
                @ToolParam(required = false, description = "rows")
                @ToolParamBounds(defaultValue = 20, min = 1, max = 50)
                Integer limit,
                @ToolParam(required = false, description = "skip")
                @ToolParamBounds(defaultValue = 0, min = 0)
                Integer offset) {
            return limit + "/" + offset;
        }

        @Tool(description = "A threshold")
        public String threshold(
                @ToolParam(required = false, description = "percent")
                @ToolParamBounds(min = 0, max = 100)
                Double pct) {
            return String.valueOf(pct);
        }

        @Tool(description = "An open range")
        public String open(
                @ToolParam(required = false, description = "percent")
                @ToolParamBounds(min = 0, max = 100, exclusiveMin = true, exclusiveMax = true)
                Double pct,
                @ToolParam(required = false, description = "share")
                @ToolParamBounds(min = 0, max = 1, exclusiveMin = true)
                Double share) {
            return pct + "/" + share;
        }
    }

    static class ExclusiveWithoutBoundTools {

        @Tool(description = "Exclusive of nothing")
        public String open(
                @ToolParam(required = false, description = "percent")
                @ToolParamBounds(max = 100, exclusiveMin = true)
                Double pct) {
            return String.valueOf(pct);
        }
    }

    static class DefaultOnExclusiveBoundTools {

        @Tool(description = "Defaults to what it refuses")
        public String open(
                @ToolParam(required = false, description = "percent")
                @ToolParamBounds(defaultValue = 100, min = 0, max = 100, exclusiveMax = true)
                Double pct) {
            return String.valueOf(pct);
        }
    }

    static class EmptyExclusiveRangeTools {

        @Tool(description = "Nothing fits")
        public String open(
                @ToolParam(required = false, description = "percent")
                @ToolParamBounds(min = 5, max = 5, exclusiveMin = true)
                Double pct) {
            return String.valueOf(pct);
        }
    }

    static class ListTools {

        @Tool(description = "Joins ids")
        public String join(@ToolParam(required = false, description = "ids") List<String> ids) {
            return String.valueOf(ids);
        }
    }

    static class DefaultOutOfBoundsTools {

        @Tool(description = "Contradicts itself")
        public String page(
                @ToolParam(required = false, description = "rows")
                @ToolParamBounds(defaultValue = 100, min = 1, max = 50)
                Integer limit) {
            return String.valueOf(limit);
        }
    }

    static class InvertedBoundsTools {

        @Tool(description = "Inverted")
        public String page(
                @ToolParam(required = false, description = "rows")
                @ToolParamBounds(min = 10, max = 5)
                Integer limit) {
            return String.valueOf(limit);
        }
    }

    static class BoundedStringTools {

        @Tool(description = "Bounds a string")
        public String find(
                @ToolParam(required = false, description = "name")
                @ToolParamBounds(max = 5)
                String name) {
            return name;
        }
    }

    static class FractionalIntegerBoundsTools {

        @Tool(description = "Half a row")
        public String page(
                @ToolParam(required = false, description = "rows")
                @ToolParamBounds(max = 5.5)
                Integer limit) {
            return String.valueOf(limit);
        }
    }

    static class IntegerListTools {

        @Tool(description = "Numbers")
        public String sum(@ToolParam(required = false, description = "ids") List<Integer> ids) {
            return String.valueOf(ids);
        }
    }

    static class NumericTools {

        @Tool(description = "First, alphabetically")
        public String alpha() {
            return "alpha";
        }

        @Tool(description = "Takes a float")
        public String beta(@ToolParam(required = false, description = "a ratio") float ratio) {
            return String.valueOf(ratio);
        }

        @Tool(description = "Takes both shapes of a number")
        public String gamma(
                @ToolParam(required = false, description = "primitive") double primitive,
                @ToolParam(required = false, description = "boxed") Double boxed) {
            return primitive + "/" + boxed;
        }
    }

    static class NamedTools {

        @Tool(name = "run_query", description = "A tool that names itself")
        public String execute() {
            return "ran";
        }
    }

    static class OverloadedTools {

        @Tool(description = "Query by name")
        public String query(@ToolParam(required = false, description = "name") String name) {
            return name;
        }

        @Tool(description = "Query by id")
        public String query(@ToolParam(required = false, description = "id") Integer id) {
            return String.valueOf(id);
        }
    }

    static class UnsupportedParamTools {

        @Tool(description = "Takes something JSON cannot carry")
        public String scan(
                @ToolParam(required = false, description = "how long") Duration window) {
            return String.valueOf(window);
        }
    }

    static class ContextTools {

        private static final McpFormSchema CONFIRM = McpFormSchema.builder()
                .required(new McpFormSchema.BooleanField(new McpFormSchema.Label("confirm", "Confirm", null), false))
                .build();

        @Tool(description = "Reports what the client can do")
        public String describe(
                @ToolParam(required = false, description = "a label") String label,
                McpCallContext call) {
            return label + ":" + call.canElicitForm() + "/" + call.tasksSupported();
        }

        @Tool(description = "Asks before it acts, when it can")
        public McpToolOutcome confirm(McpCallContext call) {
            if (call.canElicitForm()) {
                return new McpToolOutcome.InputRequired(Map.of("confirm", new McpFormElicitation("Sure?", CONFIRM)));
            }
            return McpToolResult.text("confirmed");
        }
    }

    static class AlwaysAsksTools {

        @Tool(description = "Always asks")
        public McpToolOutcome ask() {
            return new McpToolOutcome.InputRequired(Map.of("confirm", new McpFormElicitation("Sure?",
                    McpFormSchema.builder()
                            .required(new McpFormSchema.BooleanField(
                                    new McpFormSchema.Label("confirm", "Confirm", null), false))
                            .build())));
        }
    }

    static class TwoContextTools {

        @Tool(description = "Declares the context twice")
        public String twice(McpCallContext first, McpCallContext second) {
            return "twice";
        }
    }

    static class IntegerReturningTools {

        @Tool(description = "Answers with a number")
        public Integer count() {
            return 1;
        }
    }

    record Numbered(int n) {
    }

    record Stamped(Instant at) {
    }

    static class InstantOutputTools {

        @Tool(description = "Answers with a timestamp")
        @McpOutputSchema(Stamped.class)
        public McpToolResult run() {
            return McpToolResult.text("never called");
        }
    }

    static class StructuredOutcomeTools {

        @Tool(description = "Answers with a structured result or a task")
        @McpOutputSchema(Numbered.class)
        public McpToolOutcome run(McpCallContext call) {
            return new McpToolOutcome.Deferred("op-1");
        }
    }
}
