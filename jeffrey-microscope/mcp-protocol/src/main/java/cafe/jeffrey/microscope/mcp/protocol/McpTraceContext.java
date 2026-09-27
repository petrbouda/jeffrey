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
package cafe.jeffrey.microscope.mcp.protocol;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The caller's own W3C trace context, as a client sends it in {@code params._meta.traceparent} and
 * {@code params._meta.tracestate}, kept verbatim so it can be recorded on the tool-call span.
 * <p>
 * Recorded, not adopted: a server whose spans carry 64-bit ids cannot take a 128-bit trace id as the
 * span's identity without loss, so the caller's trace travels beside the server's own ids, which is
 * enough to find the tool call behind a step of the caller's trace.
 * <p>
 * Only version {@code 00} is read, exactly as the W3C Trace Context specification spells it: lower-case
 * hex, a trace id and a parent id that are not all zeros, dash-separated. Anything else is dropped
 * rather than repaired, and a {@code tracestate} goes with the {@code traceparent} it continues.
 *
 * @param traceparent the {@code traceparent}, well-formed
 * @param tracestate  the {@code tracestate}, opaque and at most {@link #MAX_TRACESTATE_CHARS}
 *                    characters, or null when the caller sent none
 */
public record McpTraceContext(String traceparent, String tracestate) {

    private static final Logger LOG = LoggerFactory.getLogger(McpTraceContext.class);

    /** The longest {@code tracestate} kept: the W3C limit a vendor must propagate at least. */
    public static final int MAX_TRACESTATE_CHARS = 512;

    /** Version {@code 00}: trace id, parent id and flags, lower-case hex, dash-separated. */
    private static final Pattern TRACEPARENT = Pattern.compile("00-([0-9a-f]{32})-([0-9a-f]{16})-[0-9a-f]{2}");
    private static final int TRACE_ID_GROUP = 1;
    private static final int PARENT_ID_GROUP = 2;

    /** A trace id or parent id of all zeros is invalid by definition. */
    private static final Pattern ALL_ZEROS = Pattern.compile("0+");

    public McpTraceContext {
        if (!isWellFormed(traceparent)) {
            throw new IllegalArgumentException("Not a version-00 W3C traceparent");
        }
        if (tracestate != null && !isKept(tracestate)) {
            throw new IllegalArgumentException(
                    "A tracestate is non-blank and at most " + MAX_TRACESTATE_CHARS + " characters");
        }
    }

    /**
     * The trace context in a request's {@code _meta}, or empty when there is none or its
     * {@code traceparent} is malformed. A malformed value is the caller's slip, not a reason to refuse
     * the call, so it is logged at debug — by its length only, never its text — and left out.
     *
     * @param meta the request's {@code params._meta}; null or not an object reads as empty
     */
    public static Optional<McpTraceContext> from(JsonNode meta) {
        if (meta == null || !meta.isObject()) {
            return Optional.empty();
        }
        JsonNode traceparent = meta.get(McpMetaKeys.TRACEPARENT);
        if (traceparent == null) {
            return Optional.empty();
        }
        if (!traceparent.isString() || !isWellFormed(traceparent.asString())) {
            LOG.debug("Ignoring a malformed trace context: key={} length={}",
                    McpMetaKeys.TRACEPARENT, lengthOf(traceparent));
            return Optional.empty();
        }
        String tracestate = tracestateOf(meta.get(McpMetaKeys.TRACESTATE));
        return Optional.of(new McpTraceContext(traceparent.asString(), tracestate));
    }

    /** The {@code tracestate} to keep beside a well-formed {@code traceparent}, or null. */
    private static String tracestateOf(JsonNode tracestate) {
        if (tracestate == null) {
            return null;
        }
        if (!tracestate.isString() || !isKept(tracestate.asString())) {
            LOG.debug("Ignoring a malformed trace context: key={} length={}",
                    McpMetaKeys.TRACESTATE, lengthOf(tracestate));
            return null;
        }
        return tracestate.asString();
    }

    private static boolean isWellFormed(String traceparent) {
        if (traceparent == null) {
            return false;
        }
        Matcher matcher = TRACEPARENT.matcher(traceparent);
        return matcher.matches()
                && !ALL_ZEROS.matcher(matcher.group(TRACE_ID_GROUP)).matches()
                && !ALL_ZEROS.matcher(matcher.group(PARENT_ID_GROUP)).matches();
    }

    private static boolean isKept(String tracestate) {
        return !tracestate.isBlank() && tracestate.length() <= MAX_TRACESTATE_CHARS;
    }

    /** How long a rejected value was, the only thing about it worth logging; -1 for one that is not text. */
    private static int lengthOf(JsonNode value) {
        return value.isString() ? value.asString().length() : -1;
    }
}
