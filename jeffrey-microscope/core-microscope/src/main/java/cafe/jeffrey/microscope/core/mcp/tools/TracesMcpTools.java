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
import cafe.jeffrey.profile.manager.model.trace.TraceOperationRow;
import cafe.jeffrey.profile.manager.model.trace.TraceOperationsPage;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.profile.resources.request.GenerateTraceSpanFlamegraphRequest;
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

    private static final int HEX_RADIX = 16;

    private static final String NO_TRACES =
            "This profile contains no traces. Traces come from the Jeffrey tracing instrumentation "
                    + "(jeffrey-events / the @Traced agent); a recording without it has none.";

    private final ProfileManager profileManager;

    public TracesMcpTools(ProfileManager profileManager) {
        this.profileManager = profileManager;
    }

    @Tool(description = "Profile-wide trace totals: how many traces and spans were recorded, how many "
            + "failed, and the latency distribution across all of them. Call this first to see whether "
            + "the profile has traces at all.")
    public String overview() {
        return McpToolOutput.json(traceManager().overview());
    }

    @Tool(description = "The operations this recording traced, one row per operation with its call "
            + "count, error count and latency percentiles. An operation is a kind of request — "
            + "'GET /orders' served over HTTP, say — identified by the triple (name, kind, eventType) "
            + "that every other traces_* tool takes.")
    public String operations(
            @ToolParam(description = "Optional case-insensitive substring matched against the operation name")
            String search,
            @ToolParam(description = "Keep only operations that failed at least once")
            Boolean errorsOnly,
            @ToolParam(description = "Order by one of: TOTAL_TIME, P50, P95, P99, MAX, COUNT, ERRORS, "
                    + "NAME. Defaults to TOTAL_TIME, which surfaces where the wall-clock actually went.")
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
                traceManager.tracesOfOperation(operationId, AI_EXPORT_EXEMPLARS_LIMIT))
                .build());
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
                    + ". One of: TOTAL_TIME, P50, P95, P99, MAX, COUNT, ERRORS, NAME.");
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
