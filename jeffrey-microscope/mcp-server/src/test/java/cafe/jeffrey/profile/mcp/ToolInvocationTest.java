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

import cafe.jeffrey.jfr.events.trace.TraceSpanEvent;
import cafe.jeffrey.microscope.mcp.protocol.McpCallContext;
import cafe.jeffrey.microscope.mcp.protocol.McpClientCapabilities;
import cafe.jeffrey.microscope.mcp.protocol.McpFormElicitation;
import cafe.jeffrey.microscope.mcp.protocol.McpFormSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpToolOutcome;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.mcp.protocol.McpTraceContext;
import cafe.jeffrey.microscope.mcp.protocol.ToolDispatchException;
import cafe.jeffrey.microscope.mcp.protocol.ToolExecutionException;
import cafe.jeffrey.shared.common.Json;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a tool's own failure looks like by the time the model reads it.
 * <p>
 * Reflection wraps everything a tool throws in an {@link java.lang.reflect.InvocationTargetException},
 * whose message is null. Handing that on would answer every failed analysis with the word "null" —
 * so what travels is the cause's message, and these pin that rather than the wrapper's.
 */
class ToolInvocationTest {

    private static Method method(String name, Class<?>... parameters) {
        try {
            return Sample.class.getMethod(name, parameters);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String invoke(String name, Object... args) {
        return invokeOutcome(name, args).requireComplete().text();
    }

    private static McpToolOutcome invokeOutcome(String name, Object... args) {
        return ToolInvocation.invoke("test_" + name, method(name, types(args)), new Sample(), args,
                McpCallContext.RESOURCE_READ);
    }

    private static Class<?>[] types(Object[] args) {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = args[i].getClass();
        }
        return types;
    }

    @Nested
    class Results {

        @Test
        void returnsWhatTheToolAnswered() {
            assertEquals("answer:hello", invoke("echo", "hello"));
        }

        /**
         * A tool that returns nothing has still answered. Empty is the honest rendering; "null" is a
         * word the model would read as data.
         */
        @Test
        void rendersANullAnswerAsEmpty() {
            assertEquals("", invoke("silent"));
        }
    }

    @Nested
    class Failures {

        /**
         * The message the tool wrote, not the wrapper reflection put around it. This is the sentence
         * the model acts on, so it is the one worth pinning.
         */
        @Test
        void reportsTheCauseRatherThanTheReflectionWrapper() {
            ToolInvocationException thrown =
                    assertThrows(ToolInvocationException.class, () -> invoke("boom"));

            assertTrue(thrown.getMessage().contains("no heap dump on this profile"), thrown.getMessage());
            assertEquals("no heap dump on this profile", thrown.getCause().getMessage());
        }

        /**
         * A refusal thrown from inside a tool body stays a tool failure, even though its type would
         * otherwise read as a bad argument. That is the right side of the line the specification
         * draws: the tool ran, decided it could not answer, and wrote a sentence for the model to act
         * on. Only the two things that happen <em>before</em> a tool runs — an unknown name and an
         * argument the schema rejects — are protocol errors, and those are
         * {@link ToolDispatchException}, which never reaches here.
         */
        /**
         * Structured content past the result limit is the tool's failure, raised inside the call as the
         * record used to raise it: the same wrapper, the same sentence underneath.
         */
        @Test
        void refusesAStructuredAnswerPastTheResultLimit() {
            ToolInvocationException thrown =
                    assertThrows(ToolInvocationException.class, () -> invokeOutcome("oversized"));

            assertEquals(IllegalArgumentException.class, thrown.getCause().getClass());
            assertEquals(McpToolResult.OVERSIZED_STRUCTURED_CONTENT, thrown.getCause().getMessage());
        }

        @Test
        void keepsAToolsOwnRefusalOnTheToolSideOfTheLine() {
            ToolInvocationException thrown =
                    assertThrows(ToolInvocationException.class, () -> invoke("refuses"));

            assertTrue(thrown.getMessage().contains("limit must be positive"), thrown.getMessage());
            assertEquals(IllegalArgumentException.class, thrown.getCause().getClass());
        }

        /**
         * The one failure that is not wrapped. A {@link ToolExecutionException} already is the
         * sentence the model is meant to read; wrapped, the client would have been given the prefix,
         * the sentence, and the sentence again out of the cause.
         */
        @Test
        void letsAToolsOwnExecutionExceptionThroughAsItself() {
            ToolExecutionException thrown =
                    assertThrows(ToolExecutionException.class, () -> invoke("declines"));

            assertEquals("no heap dump on this profile", thrown.getMessage());
        }
    }

    /**
     * A tool may answer with a question or a task instead of a result. Neither is rendered here — the
     * envelope renders them — so both travel through exactly as the tool built them.
     */
    @Nested
    class Outcomes {

        @Test
        void passesAQuestionThroughAsItself() {
            McpToolOutcome outcome = invokeOutcome("ask");

            assertSame(Sample.QUESTION, outcome);
        }

        @Test
        void passesATaskThroughAsItself() {
            McpToolOutcome.Deferred deferred = assertInstanceOf(McpToolOutcome.Deferred.class, invokeOutcome("defer"));

            assertEquals("op-7", deferred.taskId());
        }

        @Test
        void passesAResultThroughAsItself() {
            assertEquals("answered", invokeOutcome("answer").requireComplete().text());
        }

        @Test
        void rendersANullOutcomeAsAnEmptyResult() {
            assertEquals("", invokeOutcome("nothing").requireComplete().text());
        }

        /** The schema promises structured data on a result; a task has no result yet to hold it. */
        @Test
        void holdsOnlyAResultToItsDeclaredOutputSchema() {
            assertThrows(IllegalStateException.class, () -> invokeOutcome("plainButDeclared"));
            assertInstanceOf(McpToolOutcome.Deferred.class, invokeOutcome("deferDeclared"));
        }
    }

    /**
     * The span each call is recorded as. A client that sent a W3C trace context has it written, verbatim,
     * into the span's attributes beside the answer's size: Jeffrey's own 64-bit ids cannot hold a 128-bit
     * trace id, so the incoming one travels as an attribute rather than as the span's identity.
     */
    @Nested
    class Span {

        private static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        private static final String TRACESTATE = "rojo=00f067aa0ba902b7,congo=t61rcWkgMzE";

        private static final McpCallContext TRACED = new McpCallContext(McpClientCapabilities.NONE, Map.of(),
                Optional.of(new McpTraceContext(TRACEPARENT, TRACESTATE)));

        private static final McpCallContext TRACED_WITHOUT_STATE = new McpCallContext(
                McpClientCapabilities.NONE, Map.of(), Optional.of(new McpTraceContext(TRACEPARENT, null)));

        @Test
        void recordsTheIncomingTraceContextBesideTheResultSize() throws IOException {
            RecordedEvent span = spanOf("echo", TRACED);

            assertEquals("test_echo", span.getString("name"));
            JsonNode attributes = Json.readTree(span.getString("attributes"));
            assertEquals(List.of("resultChars", "traceparent", "tracestate"),
                    attributes.propertyStream().map(Map.Entry::getKey).toList());
            assertEquals("answer:hello".length(), attributes.get("resultChars").asInt());
            assertEquals(TRACEPARENT, attributes.get("traceparent").asString());
            assertEquals(TRACESTATE, attributes.get("tracestate").asString());
        }

        @Test
        void recordsATraceparentWithoutATracestate() throws IOException {
            JsonNode attributes = Json.readTree(spanOf("echo", TRACED_WITHOUT_STATE).getString("attributes"));

            assertEquals(TRACEPARENT, attributes.get("traceparent").asString());
            assertFalse(attributes.has("tracestate"), attributes.toString());
        }

        /** A resource read, or a call without a trace context, records the size alone as before. */
        @Test
        void recordsOnlyTheResultSizeWithoutATraceContext() throws IOException {
            JsonNode attributes = Json.readTree(spanOf("echo", McpCallContext.RESOURCE_READ).getString("attributes"));

            assertEquals(List.of("resultChars"), attributes.propertyStream().map(Map.Entry::getKey).toList());
        }

        /** The incoming trace is an attribute, never the span's identity: Jeffrey's own ids stay. */
        @Test
        void keepsJeffreysOwnIds() throws IOException {
            RecordedEvent span = spanOf("echo", TRACED);

            assertNotEquals(0L, span.getLong("traceId"));
            assertNotEquals(0L, span.getLong("spanId"));
            assertNotEquals(0x4bf92f3577b34da6L, span.getLong("traceId"));
            assertNotEquals(0xa3ce929d0e0e4736L, span.getLong("traceId"));
        }

        /** A failed call is the one most worth finding from the client's trace. */
        @Test
        void recordsTheTraceContextOnAFailedCall() throws IOException {
            RecordedEvent span = spanOf("boom", TRACED);

            assertEquals("ERROR", span.getString("status"));
            JsonNode attributes = Json.readTree(span.getString("attributes"));
            assertEquals(0, attributes.get("resultChars").asInt());
            assertEquals(TRACEPARENT, attributes.get("traceparent").asString());
        }

        /** An oversized answer is a failed call, and its span says so. */
        @Test
        void recordsAnOversizedAnswerAsAFailedSpan() throws IOException {
            RecordedEvent span = spanOf("oversized", TRACED);

            assertEquals("ERROR", span.getString("status"));
            assertEquals(ToolInvocationException.class.getName(), span.getString("errorType"));
            assertEquals(0, Json.readTree(span.getString("attributes")).get("resultChars").asInt());
        }

        /**
         * The context reaches the span through both toolsets, not only when the invocation is called
         * directly: each hands over the context of the call it is serving.
         */
        @Test
        void bothToolsetsHandTheCallsTraceContextToTheSpan() throws IOException {
            ReflectiveToolset reflective = new ReflectiveToolset(new TracedTools(), "reflective");
            ProfileScopedToolset<TracedTools> scoped = ProfileScopedToolset.leased(TracedTools.class, "scoped",
                    profileId -> new ProfileScopedToolset.ScopedTarget<>() {
                        @Override
                        public TracedTools target() {
                            return new TracedTools();
                        }

                        @Override
                        public void close() {
                        }
                    });

            RecordedEvent fromReflective = recorded("reflective_echo", () -> reflective.call("reflective_echo",
                    Json.createObject().put("message", "hello"), TRACED));
            RecordedEvent fromScoped = recorded("scoped_echo", () -> scoped.call("scoped_echo",
                    Json.createObject().put("profileId", "p-1").put("message", "hello"), TRACED));

            for (RecordedEvent span : List.of(fromReflective, fromScoped)) {
                JsonNode attributes = Json.readTree(span.getString("attributes"));
                assertEquals(TRACEPARENT, attributes.get("traceparent").asString(), span.getString("name"));
                assertEquals(TRACESTATE, attributes.get("tracestate").asString(), span.getString("name"));
            }
        }

        private RecordedEvent spanOf(String tool, McpCallContext context) throws IOException {
            Object[] args = "echo".equals(tool) ? new Object[]{"hello"} : new Object[0];
            return recorded("test_" + tool, () -> {
                try {
                    ToolInvocation.invoke("test_" + tool, method(tool, types(args)), new Sample(), args, context);
                } catch (RuntimeException expected) {
                    // A failed call still records its span; that span is what the test reads.
                }
            });
        }

        /** The one span named {@code spanName} that {@code call} recorded. */
        private RecordedEvent recorded(String spanName, Runnable call) throws IOException {
            Path dump = Files.createTempFile("tool-invocation", ".jfr");
            try (Recording recording = new Recording()) {
                recording.enable(TraceSpanEvent.NAME).withThreshold(Duration.ZERO);
                recording.start();
                call.run();
                recording.stop();
                recording.dump(dump);
                List<RecordedEvent> spans = RecordingFile.readAllEvents(dump).stream()
                        .filter(event -> TraceSpanEvent.NAME.equals(event.getEventType().getName()))
                        .filter(event -> spanName.equals(event.getString("name")))
                        .toList();
                assertEquals(1, spans.size(), spans.toString());
                return spans.getFirst();
            } finally {
                Files.deleteIfExists(dump);
            }
        }
    }

    public static class TracedTools {

        @Tool(description = "Echoes its message")
        public String echo(@ToolParam(required = true, description = "What to echo") String message) {
            return "answer:" + message;
        }
    }

    record Empty() {
    }

    record Rows(String rows) {
    }

    public static class Sample {

        static final McpToolOutcome QUESTION = new McpToolOutcome.InputRequired(Map.of("confirm",
                new McpFormElicitation("Sure?", McpFormSchema.builder()
                        .required(new McpFormSchema.BooleanField(
                                new McpFormSchema.Label("confirm", "Confirm", null), false))
                        .build())));

        public McpToolOutcome ask() {
            return QUESTION;
        }

        public McpToolOutcome defer() {
            return new McpToolOutcome.Deferred("op-7");
        }

        public McpToolOutcome answer() {
            return McpToolResult.text("answered");
        }

        public McpToolOutcome nothing() {
            return null;
        }

        @McpOutputSchema(Empty.class)
        public McpToolOutcome plainButDeclared() {
            return McpToolResult.text("no structure");
        }

        @McpOutputSchema(Empty.class)
        public McpToolOutcome deferDeclared() {
            return new McpToolOutcome.Deferred("op-8");
        }

        public String echo(String message) {
            return "answer:" + message;
        }

        public String silent() {
            return null;
        }

        public String boom() {
            throw new IllegalStateException("no heap dump on this profile");
        }

        public String refuses() {
            throw new IllegalArgumentException("limit must be positive");
        }

        public String declines() {
            throw new ToolExecutionException("no heap dump on this profile");
        }

        public McpToolOutcome oversized() {
            return McpToolResult.of("rows", new Rows("x".repeat(McpToolOutput.MAX_CHARS)));
        }
    }
}
