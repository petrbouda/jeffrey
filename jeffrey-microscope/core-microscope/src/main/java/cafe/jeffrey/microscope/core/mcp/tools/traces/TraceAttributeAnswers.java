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

package cafe.jeffrey.microscope.core.mcp.tools.traces;

import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;
import cafe.jeffrey.profile.manager.model.trace.TraceAttributeKeyRow;
import cafe.jeffrey.profile.manager.model.trace.TraceAttributeSearchResult;
import cafe.jeffrey.profile.manager.model.trace.TraceAttributeValues;
import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.provider.profile.api.TraceAttributeCarrier;
import cafe.jeffrey.provider.profile.api.TraceAttributeOperator;
import cafe.jeffrey.provider.profile.api.TraceAttributeScope;
import cafe.jeffrey.provider.profile.api.TraceAttributeSource;

import java.util.List;

/**
 * What the trace-attribute tools answer with: the keys a recording's traces carried, one key broken
 * into its values, and the traces that carried one value. A key is always the triple
 * {@code (source, owner, key)}, echoed as the enum constants the inputs take.
 */
public final class TraceAttributeAnswers {

    private TraceAttributeAnswers() {
    }

    public enum KeysStatus {
        OK,
        /** The recording's traces carried no attribute at all. */
        NO_ATTRIBUTES
    }

    public enum ValuesStatus {
        OK,
        /** No value of this key was recorded, in the scope asked about. */
        NO_VALUES
    }

    public enum SearchStatus {
        OK,
        /** The condition matched no trace. */
        NO_MATCH
    }

    /**
     * One key, identified by the triple the other attribute tools take.
     *
     * @param owner the event type declaring an EVENT_FIELD; null for a key without one
     */
    public record Key(
            TraceAttributeSource source,
            @McpNullable
            @McpDescription("The event type declaring an EVENT_FIELD key; null when the key has no owner")
            String owner,
            String key,
            @McpDescription("What the values are, e.g. STRING or NUMBER")
            String valueKind,
            long distinctValues,
            long carrierCount,
            long traceCount,
            @McpDescription("Too many distinct values to break down: search it with traces_attributeSearch instead")
            boolean searchOnly) {

        public static Key of(TraceAttributeKeyRow row) {
            return new Key(TraceAttributeSource.valueOf(row.source()), row.owner(), row.key(), row.valueKind(),
                    row.distinctValues(), row.carrierCount(), row.traceCount(), row.searchOnly());
        }
    }

    public record Keys(
            KeysStatus status,
            @McpNullable
            @McpDescription("Why there are no keys; null when status is OK")
            String reason,
            String profileId,
            @McpNullable
            @McpDescription("The event type the keys were narrowed to; null for every key in the profile")
            String eventType,
            @McpDescription("The keys, as many as fit the answer")
            List<Key> keys,
            @McpNullable
            @McpDescription("Keys left out of keys to fit the answer; null when status is NO_ATTRIBUTES")
            Integer omittedKeys,
            McpFollowUp followUp,
            @McpDescription("The attribute search in the Microscope UI, for the user")
            String uiLink) {
    }

    /** One value of the key, with its own latency in nanoseconds. */
    public record Value(
            String value,
            long traceCount,
            long totalNanos,
            long p50Nanos,
            long p95Nanos,
            long maxNanos,
            long errorTraces) {

        public static Value of(TraceAttributeValues.Row row) {
            return new Value(row.value(), row.traceCount(), row.totalNanos(), row.p50Nanos(), row.p95Nanos(),
                    row.maxNanos(), row.errorTraces());
        }
    }

    public record Values(
            ValuesStatus status,
            @McpNullable
            @McpDescription("Why there are no values; null when status is OK")
            String reason,
            String profileId,
            String key,
            TraceAttributeSource source,
            @McpNullable
            String owner,
            @McpNullable
            @McpDescription("The event type the values were narrowed to; null for the whole profile")
            String eventType,
            @McpDescription("The values, in the order asked for. A trace that carried two values counts under both")
            List<Value> values,
            @McpDescription("How many distinct values the key has in this scope")
            long distinctValues,
            @McpNullable
            @McpDescription("Values left out of values, counted from distinctValues; null when status is NO_VALUES")
            Long omittedValues,
            @McpDescription("Traces that never carried the key")
            long tracesWithoutKey,
            McpFollowUp followUp,
            @McpDescription("The key's value breakdown in the Microscope UI, for the user")
            String uiLink) {
    }

    /**
     * One carrier that satisfied the condition.
     *
     * @param spanId the span as a hex string; null when a notification matched outside any span
     */
    public record Hit(
            TraceAttributeCarrier carrier,
            @McpNullable
            @McpDescription("The span as a hex string; null when a notification matched outside any span")
            String spanId,
            String key,
            String value) {

        public static Hit of(TraceAttributeSearchResult.Hit hit) {
            return new Hit(hit.carrier(), hit.spanId(), hit.key(), hit.value());
        }
    }

    /** One matched trace, with the carriers that matched it, capped by the engine. */
    public record Match(TraceAnswers.Trace trace, List<Hit> hits) {

        /**
         * @param traceUiLink the matched trace's span waterfall in Microscope
         */
        public static Match of(TraceAttributeSearchResult.Match match, String traceUiLink) {
            return new Match(TraceAnswers.Trace.of(match.trace(), traceUiLink),
                    match.hits().stream().map(Hit::of).toList());
        }
    }

    public record Search(
            SearchStatus status,
            @McpNullable
            @McpDescription("Why nothing matched; null when status is OK")
            String reason,
            String profileId,
            String key,
            TraceAttributeSource source,
            @McpNullable
            String owner,
            TraceAttributeOperator operator,
            @McpNullable
            String value,
            TraceAttributeScope scope,
            @McpDescription("This page of matching traces, slowest first")
            List<Match> matches,
            @McpDescription("Traces matching the condition, across every page")
            long totalMatching,
            boolean hasMore,
            @McpNullable
            @McpDescription("Pass as cursor, with the same condition, for the next page; null when hasMore is false")
            String nextCursor,
            @McpNullable
            @McpDescription("Every matched trace summarised, durations in nanoseconds; null when status is NO_MATCH")
            TraceAttributeSearchResult.Stats stats,
            McpFollowUp followUp,
            @McpDescription("This search - the same condition and scope - in the Microscope UI, for the user")
            String uiLink) {
    }
}
