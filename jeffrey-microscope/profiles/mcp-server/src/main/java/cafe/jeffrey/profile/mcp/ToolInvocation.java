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
import cafe.jeffrey.shared.common.Json;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Invokes one {@code @Tool} method and records it as a JFR span.
 * <p>
 * The span name is the tool name: it comes from a fixed set of annotated methods, so it stays
 * low-cardinality however often the model calls it. This is what separates time spent in the model from
 * time spent in Jeffrey's own queries when a tool-assisted answer is slow. The span wraps the reflective
 * call <em>and</em> its exception translation, so a failed tool is recorded with the exception the caller
 * actually sees.
 */
final class ToolInvocation {

    /** Attribute recording how many characters a tool handed back to the model. */
    private static final String RESULT_CHARS_ATTRIBUTE = "resultChars";

    private ToolInvocation() {
    }

    static McpToolResult invoke(String toolName, Method method, Object target, Object[] args) {
        TraceSpanEvent span = new TraceSpanEvent();
        span.name = toolName;
        span.kind = SpanKind.INTERNAL.name();
        span.begin();

        McpToolResult result = null;
        try {
            result = Tracer.inSpanOf(span, () -> {
                try {
                    Object value = method.invoke(target, args);
                    McpToolResult output = value instanceof McpToolResult structured
                            ? structured
                            : McpToolResult.text(value == null ? "" : value.toString());
                    if (method.isAnnotationPresent(McpOutputSchema.class) && !output.hasStructuredContent()) {
                        throw new IllegalStateException("Tool declared an output schema but returned no structured data: "
                                + toolName);
                    }
                    return output;
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
                span.attributes = Json.toString(
                        Map.of(RESULT_CHARS_ATTRIBUTE, result == null ? 0 : result.text().length()));
                span.commit();
            }
        }
    }
}
