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

import cafe.jeffrey.profile.mcp.ToolParamValues;
import cafe.jeffrey.microscope.core.mcp.LinkedOutput;
import cafe.jeffrey.microscope.core.mcp.UiLinks;
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
import cafe.jeffrey.profile.manager.model.trace.TraceOverview;
import cafe.jeffrey.profile.manager.model.trace.TraceRow;
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
import java.util.Map;

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

    private static final String OPERATIONS_VIEW = "traces/operations";
    private static final String ATTRIBUTE_SEARCH_VIEW = "traces/attributes/search";
    private static final String OPERATION_PARAM = "operation";
    private static final String KIND_PARAM = "kind";
    private static final String EVENT_TYPE_PARAM = "eventType";
    private static final String TAB_PARAM = "tab";
    private static final String TRACE_PARAM = "trace";
    private static final String FLAMES_TAB = "flames";
    private static final String SLOWEST_TAB = "slowest";

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

    private static final String STEP_OPERATIONS =
            "traces_operations ranks the operations by where the wall-clock went; an operation is the "
                    + "triple (name, kind, eventType).";
    private static final String STEP_NOTIFICATIONS_URGENT =
            "This recording carries CRITICAL or HIGH notifications. Read traces_notifications before "
                    + "exporting anything: the application's own account of what went wrong usually "
                    + "names the cause a span tree only shows the cost of.";
    private static final String STEP_SPAN_FRAMES =
            "The span tree says where the time went, not what code was running. For the frames inside "
                    + "one span, traces_spanFlamegraphExport with that trace id and span id.";
    private static final String STEP_EXEMPLAR =
            "This is one request. Whether it is typical or an outlier is traces_operationExport for "
                    + "the whole population.";
    private static final String STEP_SLOWEST =
            "traces_slowestTraces lists individual traces of this operation, and traces_traceExport "
                    + "opens one of them span by span.";

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
        TraceOverview overview = traceManager().overview();
        return LinkedOutput.json(new TraceOverviewResult(
                overview,
                NextSteps.builder()
                        .when(overview.urgentNotificationCount() > 0, STEP_NOTIFICATIONS_URGENT)
                        .add(STEP_OPERATIONS)
                        .build(),
                UiLinks.view(profileId(), OPERATIONS_VIEW)));
    }

    @Tool(description = "The operations this recording traced, one row per operation with its call "
            + "count, error count, notification counts and latency percentiles. An operation is a kind of request — "
            + "'GET /orders' served over HTTP, say — identified by the triple (name, kind, eventType) "
            + "that every other traces_* tool takes.")
    public String operations(
            @ToolParam(required = false, description = "Optional case-insensitive substring matched against the operation name")
            String search,
            @ToolParam(required = false, description = "Keep only operations that failed at least once")
            Boolean errorsOnly,
            @ToolParam(required = false, description = "Order by one of: TOTAL_TIME, P50, P95, P99, MAX, COUNT, ERRORS, "
                    + "NOTIFICATIONS, NAME. Defaults to TOTAL_TIME, which surfaces where the wall-clock "
                    + "actually went.")
            String sort,
            @ToolParam(required = false, description = "Maximum number of operations to return (default 50)")
            Integer limit) {

        TraceOperationListQuery query = new TraceOperationListQuery(
                search,
                Boolean.TRUE.equals(errorsOnly),
                sortField(sort),
                true,
                ToolArguments.boundedLimit(limit, DEFAULT_OPERATIONS_LIMIT, MAX_LIMIT),
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
            @ToolParam(required = true, description = "Operation name, e.g. 'GET /orders'")
            String name,
            @ToolParam(required = true, description = "Span kind of the operation's root, e.g. 'SERVER' or 'CLIENT'")
            @ToolParamValues({"SERVER", "CLIENT", "INTERNAL", "PRODUCER", "CONSUMER"})
            String kind,
            @ToolParam(required = true, description = "Event type that opened the trace, e.g. 'jeffrey.HttpServerExchange'")
            String eventType) {

        TraceOperationId operationId = operationId(name, kind, eventType);
        TraceManager traceManager = traceManager();
        TraceOperationRow operation = traceManager.operation(operationId).orElse(null);
        if (operation == null) {
            return McpToolOutput.error("No such operation: " + name
                    + " (kind=" + kind + ", eventType=" + eventType + "). Use traces_operations to list them.");
        }

        String export = new TraceOperationAiMarkdownBuilder(
                operation,
                traceManager.operationSummary(operationId, AI_EXPORT_SPANS_LIMIT),
                traceManager.notifications(
                        TraceNotificationListQuery.ofOperation(operationId, AI_EXPORT_NOTIFICATION_KINDS_LIMIT)),
                traceManager.tracesOfOperation(operationId, AI_EXPORT_EXEMPLARS_LIMIT))
                .build();

        return LinkedOutput.of(
                export,
                NextSteps.builder().add(STEP_SLOWEST).build(),
                operationUrl(name, kind, eventType, null));
    }

    @Tool(description = "What the application reported about itself while traces ran: every "
            + "jeffrey.Notification raised inside a trace, grouped by kind, the most severe first. "
            + "Each row carries the type, severity, category, source and message, how many times it "
            + "was raised, in how many distinct traces, and a few exemplar trace ids (slowest first) "
            + "for traces_traceExport. This is the tool for 'what went wrong during this recording' "
            + "before any timing is read; traces_overview says how many there are in all.")
    public String notifications(
            @ToolParam(required = false, description = "Keep only one severity: CRITICAL, HIGH, MEDIUM or LOW")
            String severity,
            @ToolParam(required = false, description = "Keep only one notification type, e.g. 'CONNECTION_POOL_EXHAUSTED'")
            String type,
            @ToolParam(required = false, description = "Keep only one category, e.g. 'RESOURCE' or 'PERFORMANCE'")
            String category,
            @ToolParam(required = false, description = "Keep only notifications raised by one source component, e.g. 'hikari'")
            String source,
            @ToolParam(required = false, description = "Optional case-insensitive substring matched against the message")
            String search,
            @ToolParam(required = false, description = "Operation name, to keep only notifications raised inside traces "
                    + "of one operation; give kind and eventType with it")
            String name,
            @ToolParam(required = false, description = "Span kind of that operation's root, e.g. 'SERVER'")
            @ToolParamValues({"SERVER", "CLIENT", "INTERNAL", "PRODUCER", "CONSUMER"})
            String kind,
            @ToolParam(required = false, description = "Event type that opened that operation's traces, e.g. "
                    + "'jeffrey.HttpServerExchange'")
            String eventType,
            @ToolParam(required = false, description = "Maximum number of notification kinds to return (default 50)")
            Integer limit) {

        TraceNotificationListQuery query = new TraceNotificationListQuery(
                severity,
                type,
                category,
                source,
                search,
                optionalOperationId(name, kind, eventType),
                ToolArguments.boundedLimit(limit, DEFAULT_NOTIFICATIONS_LIMIT, MAX_LIMIT));

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
            @ToolParam(required = true, description = "Operation name, e.g. 'GET /orders'")
            String name,
            @ToolParam(required = true, description = "Span kind of the operation's root, e.g. 'SERVER'")
            @ToolParamValues({"SERVER", "CLIENT", "INTERNAL", "PRODUCER", "CONSUMER"})
            String kind,
            @ToolParam(required = true, description = "Event type that opened the trace")
            String eventType,
            @ToolParam(required = false, description = "Maximum number of traces to return (default 20)")
            Integer limit) {

        return LinkedOutput.json(new SlowestTracesResult(
                traceManager().tracesOfOperation(
                        operationId(name, kind, eventType),
                        ToolArguments.boundedLimit(limit, DEFAULT_TRACES_LIMIT, MAX_LIMIT)),
                NextSteps.builder().add(STEP_EXEMPLAR).build(),
                operationUrl(name, kind, eventType, SLOWEST_TAB)));
    }

    @Tool(description = "One trace written as Markdown for reading: its span tree with self time and "
            + "critical path, the JVM context underneath it (GC pauses, monitor waits, parks), a ranked "
            + "accounting of where its wall-clock went, its I/O shape and its exceptions. This is the "
            + "tool for 'why was this request slow'.")
    public String traceExport(
            @ToolParam(required = true, description = "Trace id as a 16-character hex string, from traces_slowestTraces")
            String traceId) {

        long id = parseId(traceId, "traceId");
        TraceManager traceManager = traceManager();
        TraceDetail detail = traceManager.trace(id).orElse(null);
        if (detail == null) {
            return McpToolOutput.error("No such trace: " + traceId);
        }
        String export = new TraceAiMarkdownBuilder(detail, traceManager.context(id)).build();
        return LinkedOutput.of(
                export,
                NextSteps.builder().add(STEP_SPAN_FRAMES).add(STEP_EXEMPLAR).build(),
                traceUrl(traceId));
    }

    @Tool(description = "A flamegraph of the samples taken while one span was open, exported as "
            + "Markdown. Answers 'what code was running inside this span', which the span tree alone "
            + "cannot say.")
    public String spanFlamegraphExport(
            @ToolParam(required = true, description = "Trace id as a 16-character hex string")
            String traceId,
            @ToolParam(required = true, description = "Span id as a 16-character hex string, from the span tree in traces_traceExport")
            String spanId,
            @ToolParam(required = true, description = "Event type to graph, e.g. 'jdk.ExecutionSample' for on-CPU time "
                    + "or 'jdk.ObjectAllocationSample' for allocation. flamegraph_list names the ones "
                    + "this profile recorded.")
            String eventType,
            @ToolParam(required = false, description = "Cut the span's children out of the window, so the graph shows only "
                    + "the work the span did itself")
            Boolean selfOnly,
            @ToolParam(required = false, description = "Split the graph per thread instead of aggregating")
            Boolean threadMode,
            @ToolParam(required = false, description = "Weigh frames by event weight instead of sample count")
            Boolean useWeight) {

        List<SpanInterval> intervals = traceManager().spanIntervals(
                parseId(traceId, "traceId"), parseId(spanId, "spanId"), Boolean.TRUE.equals(selfOnly));
        if (intervals.isEmpty()) {
            return McpToolOutput.error("Span has no samples to show: " + spanId);
        }
        return exportScoped(SpanScope.of(intervals), eventType, threadMode, useWeight, traceUrl(traceId));
    }

    @Tool(description = "A flamegraph of the samples taken while any trace of one operation was "
            + "running, exported as Markdown. Answers 'what does this kind of request spend its time "
            + "on' across every occurrence, rather than in one exemplar.")
    public String operationFlamegraphExport(
            @ToolParam(required = true, description = "Operation name, e.g. 'GET /orders'")
            String name,
            @ToolParam(required = true, description = "Span kind of the operation's root, e.g. 'SERVER'")
            @ToolParamValues({"SERVER", "CLIENT", "INTERNAL", "PRODUCER", "CONSUMER"})
            String kind,
            @ToolParam(required = true, description = "Event type that opened the trace, e.g. 'jeffrey.HttpServerExchange'")
            String eventType,
            @ToolParam(required = true, description = "Event type to graph, e.g. 'jdk.ExecutionSample'. Different from "
                    + "eventType, which identifies the operation: this one names the samples to draw, "
                    + "and flamegraph_list names the ones this profile recorded.")
            String graphEventType,
            @ToolParam(required = false, description = "Split the graph per thread instead of aggregating")
            Boolean threadMode,
            @ToolParam(required = false, description = "Weigh frames by event weight instead of sample count")
            Boolean useWeight) {

        requireText(name, "name");
        requireText(kind, "kind");
        requireText(eventType, "eventType");
        return exportScoped(
                new SpanScope.Operation(name, kind, eventType), graphEventType, threadMode, useWeight,
                operationUrl(name, kind, eventType, FLAMES_TAB));
    }

    /**
     * A span-scoped flamegraph, built exactly as the drawn one next to it in the UI so the two describe
     * the same samples.
     * <p>
     * The event type to graph is required rather than defaulted. There is no answer that is right for
     * every profile — a recording made with the CPU-time sampler carries no {@code jdk.ExecutionSample}
     * at all — and a default would draw an empty graph for exactly those profiles, which reads as "this
     * span ran no code" rather than as "you asked for samples this recording does not hold".
     */
    private String exportScoped(
            SpanScope scope, String graphEventType, Boolean threadMode, Boolean useWeight, String url) {

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
        return LinkedOutput.of(profileManager.flamegraphManager().generateAiExport(params), url);
    }

    /**
     * The operations page showing one operation, optionally on one of its tabs. All three parts of the
     * identity travel: a link carrying only the name would resolve to whichever of an inbound and an
     * outbound call of that name came first.
     */
    private String operationUrl(String name, String kind, String eventType, String tab) {
        Map<String, String> query = UiLinks.query();
        query.put(OPERATION_PARAM, name);
        query.put(KIND_PARAM, kind);
        query.put(EVENT_TYPE_PARAM, eventType);
        query.put(TAB_PARAM, tab);
        return UiLinks.view(profileId(), OPERATIONS_VIEW, query);
    }

    /**
     * One trace's span waterfall. Addressed through the attribute-search page because that view opens
     * the waterfall from the id alone - the operations page resolves a trace against the rows it has
     * loaded, which a bare id cannot assume.
     */
    private String traceUrl(String traceId) {
        Map<String, String> query = UiLinks.query();
        query.put(TRACE_PARAM, traceId);
        return UiLinks.view(profileId(), ATTRIBUTE_SEARCH_VIEW, query);
    }

    private String profileId() {
        return profileManager.info().id();
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

    /**
     * The link travels as a field rather than as a line appended after the JSON, so the answer stays
     * parseable as the one document it claims to be.
     */
    private record TraceOverviewResult(
            TraceOverview overview, List<String> nextSteps, String uiLink) {
    }

    private record SlowestTracesResult(
            List<TraceRow> traces, List<String> nextSteps, String uiLink) {
    }
}
