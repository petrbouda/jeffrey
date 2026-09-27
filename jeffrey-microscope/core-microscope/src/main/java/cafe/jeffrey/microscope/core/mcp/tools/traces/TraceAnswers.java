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
import cafe.jeffrey.profile.manager.model.trace.TraceOperationRow;
import cafe.jeffrey.profile.manager.model.trace.TraceOverview;
import cafe.jeffrey.profile.manager.model.trace.TraceRow;
import cafe.jeffrey.profile.mcp.McpFollowUp;

import java.util.List;

/**
 * What the {@code traces_} operation, trace and notification tools answer with. Each is the tool's
 * whole answer: its figures, its status where it can have none, the next calls and the Microscope page
 * that shows the same thing. The Markdown exports keep their Markdown as the text; their record says
 * what the export was built from.
 */
public final class TraceAnswers {

    private static final String REASON = "Why there is nothing to list; null when status is OK";
    private static final String MARKDOWN_CHARS = "Characters of the Markdown export, before the text cap";
    private static final String TRUNCATED = "Whether the Markdown was cut to fit the text cap with its footer";

    private TraceAnswers() {
    }

    /** Whether the profile carries traces at all. */
    public enum OverviewStatus {
        OK,
        /** The recording has no traces: it was not made with the Jeffrey tracing instrumentation. */
        NO_TRACES
    }

    public enum OperationsStatus {
        OK,
        /** The recording has no traces at all. */
        NO_TRACES,
        /** The recording has traces, but no operation matches the filters. */
        NO_MATCH
    }

    public enum NotificationsStatus {
        OK,
        /** The recording has no traces at all. */
        NO_TRACES,
        /** The recording has traces, and the application raised no notification inside them. */
        NO_NOTIFICATIONS,
        /** Notifications were raised, but none matches the filters. */
        NO_MATCH
    }

    public enum SpanFlamegraphStatus {
        OK,
        /** selfOnly was asked for, and the span's children covered all of its time. */
        NO_SELF_TIME
    }

    /**
     * One trace, with one instant on the epoch clock.
     *
     * @param traceId the 16-character hex id traces_traceExport takes
     */
    public record Trace(
            @McpDescription("The trace id as a hex string, the one traces_traceExport takes")
            String traceId,
            String rootName,
            String rootKind,
            String rootEventType,
            @McpDescription("When the trace started, as UTC epoch milliseconds")
            long startEpochMs,
            long durationNanos,
            int spanCount,
            int errorCount,
            @McpDescription("Whether a span of this trace was synthesised from a platform event, such as a socket read")
            boolean hasPlatformSpan) {

        public static Trace of(TraceRow row) {
            return new Trace(row.traceId(), row.rootName(), row.rootKind(), row.rootEventType(),
                    row.startEpochMillis(), row.durationNanos(), row.spanCount(), row.errorCount(),
                    row.hasPlatformSpan());
        }
    }

    public record Overview(
            OverviewStatus status,
            @McpNullable
            @McpDescription(REASON)
            String reason,
            String profileId,
            @McpNullable
            @McpDescription("The profile-wide totals, durations in nanoseconds; null when status is NO_TRACES")
            TraceOverview overview,
            McpFollowUp followUp,
            @McpDescription("The traced operations in the Microscope UI, for the user")
            String uiLink) {
    }

    public record Operations(
            OperationsStatus status,
            @McpNullable
            @McpDescription(REASON)
            String reason,
            String profileId,
            @McpDescription("This page of operations, durations in nanoseconds")
            List<TraceOperationRow> operations,
            @McpDescription("Operations matching the filters, across every page")
            long totalMatching,
            boolean hasMore,
            @McpNullable
            @McpDescription("Pass as cursor, with the same filters, for the next page; null when hasMore is false")
            String nextCursor,
            McpFollowUp followUp,
            @McpDescription("The traced operations in the Microscope UI, for the user")
            String uiLink) {
    }

    /**
     * One kind of notification, the instants on the epoch clock.
     *
     * @param exemplarTraceIds a few traces that raised it, slowest first, for traces_traceExport
     */
    public record NotificationGroup(
            @McpNullable
            @McpDescription("The notification type as the application wrote it; null when it wrote none")
            String type,
            @McpNullable
            @McpDescription("Normally CRITICAL, HIGH, MEDIUM or LOW, as the application wrote it; null when it wrote none")
            String severity,
            @McpNullable
            String category,
            @McpNullable
            String source,
            @McpNullable
            String message,
            long count,
            long traceCount,
            @McpNullable
            @McpDescription("When it was first raised, as UTC epoch milliseconds; null when the profile has no recording span")
            Long firstEpochMs,
            @McpNullable
            @McpDescription("When it was last raised, as UTC epoch milliseconds; null when the profile has no recording span")
            Long lastEpochMs,
            List<String> exemplarTraceIds) {
    }

    public record Notifications(
            NotificationsStatus status,
            @McpNullable
            @McpDescription(REASON)
            String reason,
            String profileId,
            @McpDescription("The notification kinds, the most severe first")
            List<NotificationGroup> groups,
            @McpNullable
            @McpDescription("Kinds left out of groups to fit the answer; null when the list reached limit and more "
                    + "may exist, or when status is not OK")
            Integer omittedGroups,
            McpFollowUp followUp,
            @McpDescription("The traced operations in the Microscope UI, for the user")
            String uiLink,
            @McpDescription("What uiLink cannot show: Microscope has no page listing notifications")
            String uiLinkNote) {
    }

    public record SlowestTraces(
            String profileId,
            String name,
            String kind,
            String eventType,
            @McpDescription("The operation's traces, slowest first")
            List<Trace> traces,
            @McpNullable
            @McpDescription("Traces left out to fit the answer; null when the list reached limit and more may exist")
            Integer omittedTraces,
            McpFollowUp followUp,
            @McpDescription("The operation's slowest traces in the Microscope UI, for the user")
            String uiLink) {
    }

    /** What a trace export was built from, beside the Markdown that is its text. */
    public record TraceExport(
            String profileId,
            String traceId,
            @McpDescription(MARKDOWN_CHARS)
            int markdownChars,
            @McpDescription(TRUNCATED)
            boolean truncated,
            McpFollowUp followUp,
            @McpDescription("The trace's span waterfall in the Microscope UI, for the user")
            String uiLink) {
    }

    /** What an operation export was built from, beside the Markdown that is its text. */
    public record OperationExport(
            String profileId,
            String name,
            String kind,
            String eventType,
            @McpDescription(MARKDOWN_CHARS)
            int markdownChars,
            @McpDescription(TRUNCATED)
            boolean truncated,
            McpFollowUp followUp,
            @McpDescription("The operation in the Microscope UI, for the user")
            String uiLink) {
    }

    /** What a span flamegraph was built from, beside the Markdown that is its text. */
    public record SpanFlamegraph(
            SpanFlamegraphStatus status,
            @McpNullable
            @McpDescription("Why there is no graph; null when status is OK")
            String reason,
            String profileId,
            String traceId,
            String spanId,
            String eventType,
            boolean selfOnly,
            boolean threadMode,
            @McpNullable
            @McpDescription("Whether frames were weighed rather than counted; null for the event type's default")
            Boolean useWeight,
            @McpDescription(MARKDOWN_CHARS + "; 0 when there is no graph")
            int markdownChars,
            @McpDescription(TRUNCATED)
            boolean truncated,
            McpFollowUp followUp,
            @McpDescription("The trace's span waterfall in the Microscope UI, where the span's flamegraph is drawn")
            String uiLink,
            @McpDescription("What uiLink cannot reproduce: the span, event type and flags are chosen on the page")
            String uiLinkNote) {
    }

    /** What an operation flamegraph was built from, beside the Markdown that is its text. */
    public record OperationFlamegraph(
            String profileId,
            String name,
            String kind,
            String eventType,
            String graphEventType,
            boolean threadMode,
            @McpNullable
            @McpDescription("Whether frames were weighed rather than counted; null for the event type's default")
            Boolean useWeight,
            @McpDescription(MARKDOWN_CHARS)
            int markdownChars,
            @McpDescription(TRUNCATED)
            boolean truncated,
            McpFollowUp followUp,
            @McpDescription("The operation's flamegraphs tab in the Microscope UI, for the user")
            String uiLink,
            @McpDescription("What uiLink cannot reproduce: the tab draws its own panels, not this event type and flags")
            String uiLinkNote) {
    }
}
