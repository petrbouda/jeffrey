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

    static String invoke(String toolName, Method method, Object target, Object[] args) {
        TraceSpanEvent span = new TraceSpanEvent();
        span.name = toolName;
        span.kind = SpanKind.INTERNAL.name();
        span.begin();

        String result = null;
        try {
            result = Tracer.inSpanOf(span, () -> {
                try {
                    Object value = method.invoke(target, args);
                    return value == null ? "" : value.toString();
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Failed to invoke tool: " + toolName, e);
                } catch (InvocationTargetException e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    throw new IllegalStateException("Tool execution failed: " + cause.getMessage(), cause);
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
                        Map.of(RESULT_CHARS_ATTRIBUTE, result == null ? 0 : result.length()));
                span.commit();
            }
        }
    }
}
