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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.core.web.controllers.profile.SpanScopedGraphParameters;
import cafe.jeffrey.profile.ai.trace.TraceAiMarkdownBuilder;
import cafe.jeffrey.profile.ai.trace.TraceOperationAiMarkdownBuilder;
import cafe.jeffrey.profile.common.config.GraphComponents;
import cafe.jeffrey.profile.common.config.GraphParameters;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.TraceManager;
import cafe.jeffrey.profile.manager.model.trace.TraceDetail;
import cafe.jeffrey.profile.manager.model.trace.TraceNotificationGroupRow;
import cafe.jeffrey.profile.manager.model.trace.TraceOperationRow;
import cafe.jeffrey.profile.manager.model.trace.TraceOperationsPage;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.profile.resources.request.GenerateTraceSpanFlamegraphRequest;
import cafe.jeffrey.provider.profile.api.TraceNotificationListQuery;
import cafe.jeffrey.provider.profile.api.TraceOperationId;
import cafe.jeffrey.provider.profile.api.TraceOperationListQuery;
import cafe.jeffrey.provider.profile.api.TraceOperationSortField;
import cafe.jeffrey.shared.common.model.SpanInterval;
import cafe.jeffrey.shared.common.model.SpanScope;
import cafe.jeffrey.shared.common.model.Type;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.Locale;

/**
 * Jeffrey traces: what the application was asked to do, and what the JVM was doing underneath while it
 * did it.
 * <p>
 * The exports are the interesting part. A trace document carries the span tree together with the GC
 * pauses and waits that crossed it, and a span flamegraph shows the frames sampled while one span was
 * open — questions ordinary tracing cannot answer, because the spans and the profiler samples live in
 * the same recording here.
 */
public class TracesMcpTools {

    private static final int DEFAULT_OPERATIONS_LIMIT = 50;
    private static final int DEFAULT_TRACES_LIMIT = 20;
    private static final int MAX_LIMIT = 1000;

    /**
     * How much of an operation an AI bundle carries. Wider than the UI's twenty, because a reader
     * scrolls and a model does not, and narrow enough that the document still fits a chat window.
     */
    private static final int AI_EXPORT_SPANS_LIMIT = 40;
    /** Exemplars to name at the end of an operation bundle, as candidates to export individually. */
    private static final int AI_EXPORT_EXEMPLARS_LIMIT = 10;
    /** Notification kinds an operation bundle carries, the most severe first. */
    private static final int AI_EXPORT_NOTIFICATION_KINDS_LIMIT = 25;
    private static final int DEFAULT_NOTIFICATIONS_LIMIT = 50;

    private static final int HEX_RADIX = 16;

    private static final String NO_TRACES =
            "This profile contains no traces. Traces come from the Jeffrey tracing instrumentation "
                    + "(jeffrey-events / the @Traced agent); a recording without it has none.";
    private static final String NO_NOTIFICATIONS =
            "The application raised no notifications inside any trace of this profile. Notifications "
                    + "are jeffrey.Notification events an instrumented application emits about itself; "
                    + "a recording without them has none to report.";
    private static final String NO_NOTIFICATIONS_MATCHED =
            "No notification matches the given filters. Call traces_notifications without filters to "
                    + "see every kind the profile holds.";

    private final ProfileManager profileManager;

    public TracesMcpTools(ProfileManager profileManager) {
        this.profileManager = profileManager;
    }

    @Tool(description = "Profile-wide trace totals: how many traces and spans were recorded, how many "
            + "failed, how many notifications the application raised inside them (and how many of "
            + "those were CRITICAL or HIGH), and the latency distribution across all of them. Call "
            + "this first to see whether the profile has traces at all.")
    public String overview() {
        return McpToolOutput.json(traceManager().overview());
    }

    @Tool(description = "The operations this recording traced, one row per operation with its call "
            + "count, error count, notification counts and latency percentiles. An operation is a kind of request — "
            + "'GET /orders' served over HTTP, say — identified by the triple (name, kind, eventType) "
            + "that every other traces_* tool takes.")
    public String operations(
            @ToolParam(description = "Optional case-insensitive substring matched against the operation name")
            String search,
            @ToolParam(description = "Keep only operations that failed at least once")
            Boolean errorsOnly,
            @ToolParam(description = "Order by one of: TOTAL_TIME, P50, P95, P99, MAX, COUNT, ERRORS, "
                    + "NOTIFICATIONS, NAME. Defaults to TOTAL_TIME, which surfaces where the wall-clock "
                    + "actually went.")
            String sort,
            @ToolParam(description = "Maximum number of operations to return (default 50)")
            Integer limit) {

        TraceOperationListQuery query = new TraceOperationListQuery(
                search,
                Boolean.TRUE.equals(errorsOnly),
                sortField(sort),
                true,
                boundedLimit(limit, DEFAULT_OPERATIONS_LIMIT),
                0);

        TraceOperationsPage page = traceManager().operations(query);
        if (page.operations().isEmpty() && (search == null || search.isBlank())
                && !Boolean.TRUE.equals(errorsOnly)) {
            // An empty page with no filter applied is not "nothing matched", it is "nothing to match".
            return NO_TRACES;
        }
        return McpToolOutput.json(page);
    }

    @Tool(description = "One operation written as Markdown for reading: its latency percentiles, where "
            + "its time went broken down by span, and its slowest individual traces named as exemplars "
            + "to export next with traces_traceExport.")
    public String operationExport(
            @ToolParam(description = "Operation name, e.g. 'GET /orders'")
            String name,
            @ToolParam(description = "Span kind of the operation's root, e.g. 'SERVER' or 'CLIENT'")
            String kind,
            @ToolParam(description = "Event type that opened the trace, e.g. 'jeffrey.HttpServerExchange'")
            String eventType) {

        TraceOperationId operationId = operationId(name, kind, eventType);
        TraceManager traceManager = traceManager();
        TraceOperationRow operation = traceManager.operation(operationId).orElse(null);
        if (operation == null) {
            return McpToolOutput.error("No such operation: " + name
                    + " (kind=" + kind + ", eventType=" + eventType + "). Use traces_operations to list them.");
        }

        return McpToolOutput.capped(new TraceOperationAiMarkdownBuilder(
                operation,
                traceManager.operationSummary(operationId, AI_EXPORT_SPANS_LIMIT),
                traceManager.notifications(
                        TraceNotificationListQuery.ofOperation(operationId, AI_EXPORT_NOTIFICATION_KINDS_LIMIT)),
                traceManager.tracesOfOperation(operationId, AI_EXPORT_EXEMPLARS_LIMIT))
                .build());
    }

    @Tool(description = "What the application reported about itself while traces ran: every "
            + "jeffrey.Notification raised inside a trace, grouped by kind, the most severe first. "
            + "Each row carries the type, severity, category, source and message, how many times it "
            + "was raised, in how many distinct traces, and a few exemplar trace ids (slowest first) "
            + "for traces_traceExport. This is the tool for 'what went wrong during this recording' "
            + "before any timing is read; traces_overview says how many there are in all.")
    public String notifications(
            @ToolParam(description = "Keep only one severity: CRITICAL, HIGH, MEDIUM or LOW")
            String severity,
            @ToolParam(description = "Keep only one notification type, e.g. 'CONNECTION_POOL_EXHAUSTED'")
            String type,
            @ToolParam(description = "Keep only one category, e.g. 'RESOURCE' or 'PERFORMANCE'")
            String category,
            @ToolParam(description = "Keep only notifications raised by one source component, e.g. 'hikari'")
            String source,
            @ToolParam(description = "Optional case-insensitive substring matched against the message")
            String search,
            @ToolParam(description = "Operation name, to keep only notifications raised inside traces "
                    + "of one operation; give kind and eventType with it")
            String name,
            @ToolParam(description = "Span kind of that operation's root, e.g. 'SERVER'")
            String kind,
            @ToolParam(description = "Event type that opened that operation's traces, e.g. "
                    + "'jeffrey.HttpServerExchange'")
            String eventType,
            @ToolParam(description = "Maximum number of notification kinds to return (default 50)")
            Integer limit) {

        TraceNotificationListQuery query = new TraceNotificationListQuery(
                severity,
                type,
                category,
                source,
                search,
                optionalOperationId(name, kind, eventType),
                boundedLimit(limit, DEFAULT_NOTIFICATIONS_LIMIT));

        List<TraceNotificationGroupRow> groups = traceManager().notifications(query);
        if (groups.isEmpty()) {
            if (query.isFiltered()) {
                return NO_NOTIFICATIONS_MATCHED;
            }
            return traceManager().overview().totalTraces() == 0 ? NO_TRACES : NO_NOTIFICATIONS;
        }
        return McpToolOutput.json(groups);
    }

    @Tool(description = "Individual traces of one operation, slowest first, with their ids. Use the id "
            + "of an interesting one with traces_traceExport.")
    public String slowestTraces(
            @ToolParam(description = "Operation name, e.g. 'GET /orders'")
            String name,
            @ToolParam(description = "Span kind of the operation's root, e.g. 'SERVER'")
            String kind,
            @ToolParam(description = "Event type that opened the trace")
            String eventType,
            @ToolParam(description = "Maximum number of traces to return (default 20)")
            Integer limit) {

        return McpToolOutput.json(traceManager().tracesOfOperation(
                operationId(name, kind, eventType), boundedLimit(limit, DEFAULT_TRACES_LIMIT)));
    }

    @Tool(description = "One trace written as Markdown for reading: its span tree with self time and "
            + "critical path, the JVM context underneath it (GC pauses, monitor waits, parks), a ranked "
            + "accounting of where its wall-clock went, its I/O shape and its exceptions. This is the "
            + "tool for 'why was this request slow'.")
    public String traceExport(
            @ToolParam(description = "Trace id as a 16-character hex string, from traces_slowestTraces")
            String traceId) {

        long id = parseId(traceId, "traceId");
        TraceManager traceManager = traceManager();
        TraceDetail detail = traceManager.trace(id).orElse(null);
        if (detail == null) {
            return McpToolOutput.error("No such trace: " + traceId);
        }
        return McpToolOutput.capped(new TraceAiMarkdownBuilder(detail, traceManager.context(id)).build());
    }

    @Tool(description = "A flamegraph of the samples taken while one span was open, exported as "
            + "Markdown. Answers 'what code was running inside this span', which the span tree alone "
            + "cannot say.")
    public String spanFlamegraphExport(
            @ToolParam(description = "Trace id as a 16-character hex string")
            String traceId,
            @ToolParam(description = "Span id as a 16-character hex string, from the span tree in traces_traceExport")
            String spanId,
            @ToolParam(description = "Event type to graph, e.g. 'jdk.ExecutionSample'")
            String eventType,
            @ToolParam(description = "Cut the span's children out of the window, so the graph shows only "
                    + "the work the span did itself")
            Boolean selfOnly,
            @ToolParam(description = "Split the graph per thread instead of aggregating")
            Boolean threadMode,
            @ToolParam(description = "Weigh frames by event weight instead of sample count")
            Boolean useWeight) {

        List<SpanInterval> intervals = traceManager().spanIntervals(
                parseId(traceId, "traceId"), parseId(spanId, "spanId"), Boolean.TRUE.equals(selfOnly));
        if (intervals.isEmpty()) {
            return McpToolOutput.error("Span has no samples to show: " + spanId);
        }
        return exportScoped(SpanScope.of(intervals), eventType, threadMode, useWeight);
    }

    @Tool(description = "A flamegraph of the samples taken while any trace of one operation was "
            + "running, exported as Markdown. Answers 'what does this kind of request spend its time "
            + "on' across every occurrence, rather than in one exemplar.")
    public String operationFlamegraphExport(
            @ToolParam(description = "Operation name, e.g. 'GET /orders'")
            String name,
            @ToolParam(description = "Span kind of the operation's root, e.g. 'SERVER'")
            String kind,
            @ToolParam(description = "Event type that opened the trace, e.g. 'jeffrey.HttpServerExchange'")
            String eventType,
            @ToolParam(description = "Event type to graph, e.g. 'jdk.ExecutionSample'. Different from "
                    + "eventType, which identifies the operation.")
            String graphEventType,
            @ToolParam(description = "Split the graph per thread instead of aggregating")
            Boolean threadMode,
            @ToolParam(description = "Weigh frames by event weight instead of sample count")
            Boolean useWeight) {

        requireText(name, "name");
        requireText(kind, "kind");
        requireText(eventType, "eventType");
        return exportScoped(
                new SpanScope.Operation(name, kind, eventType), graphEventType, threadMode, useWeight);
    }

    /**
     * A span-scoped flamegraph, built exactly as the drawn one next to it in the UI so the two describe
     * the same samples.
     */
    private String exportScoped(
            SpanScope scope, String graphEventType, Boolean threadMode, Boolean useWeight) {

        Type type = FlamegraphMcpTools.requireEventType(graphEventType);
        GenerateTraceSpanFlamegraphRequest request = new GenerateTraceSpanFlamegraphRequest(
                false,
                type,
                Boolean.TRUE.equals(threadMode),
                useWeight,
                false,
                false,
                false,
                GraphComponents.FLAMEGRAPH_ONLY);

        GraphParameters params = SpanScopedGraphParameters.of(profileManager.info(), request, scope);
        return McpToolOutput.capped(profileManager.flamegraphManager().generateAiExport(params));
    }

    private TraceManager traceManager() {
        return profileManager.traceManager();
    }

    /**
     * The operation triple when one was given, and nothing when none was — refusing the half-given
     * case, since a name without its kind and event type names two operations as often as one.
     */
    private static TraceOperationId optionalOperationId(String name, String kind, String eventType) {
        boolean anyGiven = isGiven(name) || isGiven(kind) || isGiven(eventType);
        if (!anyGiven) {
            return null;
        }
        if (!(isGiven(name) && isGiven(kind) && isGiven(eventType))) {
            throw new IllegalArgumentException(
                    "An operation is identified by all three of name, kind and eventType; give all of "
                            + "them or none. Use traces_operations to list them.");
        }
        return operationId(name, kind, eventType);
    }

    private static boolean isGiven(String value) {
        return value != null && !value.isBlank();
    }

    private static TraceOperationId operationId(String name, String kind, String eventType) {
        return new TraceOperationId(
                requireText(name, "name"),
                requireText(kind, "kind"),
                requireText(eventType, "eventType"));
    }

    private static TraceOperationSortField sortField(String sort) {
        if (sort == null || sort.isBlank()) {
            return TraceOperationSortField.TOTAL_TIME;
        }
        try {
            return TraceOperationSortField.valueOf(sort.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown sort field: " + sort
                    + ". One of: TOTAL_TIME, P50, P95, P99, MAX, COUNT, ERRORS, NOTIFICATIONS, NAME.");
        }
    }

    private static int boundedLimit(Integer limit, int fallback) {
        if (limit == null || limit < 1) {
            return fallback;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private static String requireText(String value, String argument) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(argument + " is required");
        }
        return value;
    }

    /**
     * Ids arrive as unsigned 16-char hex, so the full 64-bit range round-trips — including the half of
     * it that is negative as a signed {@code long}.
     */
    private static long parseId(String hex, String argument) {
        requireText(hex, argument);
        try {
            return Long.parseUnsignedLong(hex.trim(), HEX_RADIX);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    argument + " must be a 16-character hex id, got: " + hex);
        }
    }
}
