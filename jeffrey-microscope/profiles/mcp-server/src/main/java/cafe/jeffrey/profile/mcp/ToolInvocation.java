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

import cafe.jeffrey.jfr.events.trace.SpanKind;
import cafe.jeffrey.jfr.events.trace.SpanStatus;
import cafe.jeffrey.jfr.events.trace.TraceSpanEvent;
import cafe.jeffrey.jfr.events.trace.Tracer;
import cafe.jeffrey.microscope.mcp.protocol.McpCallContext;
import cafe.jeffrey.microscope.mcp.protocol.McpMetaKeys;
import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpToolOutcome;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.mcp.protocol.ToolExecutionException;
import cafe.jeffrey.shared.common.Json;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Invokes one {@code @Tool} method and records it as a JFR span.
 * <p>
 * The span name is the tool name: it comes from a fixed set of annotated methods, so it stays
 * low-cardinality however often the model calls it. This is what separates time spent in the model from
 * time spent in Jeffrey's own queries when a tool-assisted answer is slow. The span wraps the reflective
 * call <em>and</em> its exception translation, so a failed tool is recorded with the exception the caller
 * actually sees.
 * <p>
 * A call that carried the caller's W3C trace context has it written into the span's attributes, verbatim,
 * beside the size of the answer. The span keeps Jeffrey's own ids: they are 64-bit and a W3C trace id is
 * 128-bit, so the caller's trace is something the span records rather than something it becomes.
 */
final class ToolInvocation {

    /** Attribute recording how many characters a tool handed back to the model. */
    private static final String RESULT_CHARS_ATTRIBUTE = "resultChars";

    /** The caller's trace context, under the names the W3C header and the request's {@code _meta} use. */
    private static final String TRACEPARENT_ATTRIBUTE = McpMetaKeys.TRACEPARENT;
    private static final String TRACESTATE_ATTRIBUTE = McpMetaKeys.TRACESTATE;

    private ToolInvocation() {
    }

    /**
     * @param context the call being served; its trace context, if any, is recorded on the span
     * @return what the tool answered: a result as it built it (text wrapped as one), or the question or
     *         task it answered with instead, untouched — rendering those is the envelope's business
     */
    static McpToolOutcome invoke(
            String toolName, Method method, Object target, Object[] args, McpCallContext context) {
        TraceSpanEvent span = new TraceSpanEvent();
        span.name = toolName;
        span.kind = SpanKind.INTERNAL.name();
        span.begin();

        McpToolOutcome result = null;
        try {
            result = Tracer.inSpanOf(span, () -> {
                try {
                    McpToolOutcome outcome = outcomeOf(method.invoke(target, args));
                    // Only a result can carry structured data; a question or a task has none to give yet.
                    if (outcome instanceof McpToolResult output
                            && method.isAnnotationPresent(McpOutputSchema.class)
                            && !output.hasStructuredContent()) {
                        throw new IllegalStateException("Tool declared an output schema but returned no structured data: "
                                + toolName);
                    }
                    // Past Jeffrey's result limit, a structured answer is the tool's own failure, raised
                    // here inside the call: the span records it, a resource read refuses it -32602 and a
                    // completion comes back empty, exactly as when the record itself refused to exist.
                    if (outcome instanceof McpToolResult output && output.exceeds(McpToolOutput.MAX_CHARS)) {
                        throw new ToolInvocationException(
                                new IllegalArgumentException(McpToolResult.OVERSIZED_STRUCTURED_CONTENT));
                    }
                    return outcome;
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Failed to invoke tool: " + toolName, e);
                } catch (InvocationTargetException e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    if (cause instanceof ToolExecutionException failure) {
                        // The tool wrote this for the model. Wrapping it would put the prefix in
                        // front of the sentence and the sentence itself in the cause, doubled.
                        throw failure;
                    }
                    throw new ToolInvocationException(cause);
                }
            });
            // A tool that returned a result did its job; the outcome is observed, not assumed.
            span.status = SpanStatus.OK.name();
            return result;
        } catch (RuntimeException e) {
            span.status = SpanStatus.ERROR.name();
            span.errorType = e.getClass().getName();
            throw e;
        } finally {
            span.end();
            if (span.shouldCommit()) {
                // How much the tool handed back. Everything a tool returns is pasted into the model's
                // context and paid for on the next round trip, so the size of the answer is as
                // interesting as the time it took to produce -- and invisible from the duration.
                span.attributes = Json.toString(attributes(result, context));
                span.commit();
            }
        }
    }

    /**
     * What the method returned, as an outcome. A tool that returns nothing has still answered: empty is
     * the honest rendering, where "null" is a word the model would read as data.
     */
    private static McpToolOutcome outcomeOf(Object value) {
        if (value instanceof McpToolOutcome outcome) {
            return outcome;
        }
        return McpToolResult.text(value == null ? "" : value.toString());
    }

    /**
     * What the span records beyond its timing, in a fixed order: the size of the answer, then the
     * caller's trace context when the call carried one.
     */
    private static Map<String, Object> attributes(McpToolOutcome result, McpCallContext context) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put(RESULT_CHARS_ATTRIBUTE, resultChars(result));
        context.trace().ifPresent(trace -> {
            attributes.put(TRACEPARENT_ATTRIBUTE, trace.traceparent());
            if (trace.tracestate() != null) {
                attributes.put(TRACESTATE_ATTRIBUTE, trace.tracestate());
            }
        });
        return attributes;
    }

    /** The characters handed to the model; a question or a task hands it none of the tool's own. */
    private static int resultChars(McpToolOutcome outcome) {
        return outcome instanceof McpToolResult result ? result.text().length() : 0;
    }
}
