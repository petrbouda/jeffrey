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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies;
import cafe.jeffrey.microscope.core.mcp.LinkedOutput;
import cafe.jeffrey.microscope.core.mcp.MicroscopeView;
import cafe.jeffrey.microscope.core.mcp.UiLinks;
import cafe.jeffrey.microscope.core.mcp.tools.traces.TraceAnswers.ExemplarTrace;
import cafe.jeffrey.microscope.core.mcp.tools.traces.TraceAnswers.NotificationGroup;
import cafe.jeffrey.microscope.core.mcp.tools.traces.TraceAnswers.Notifications;
import cafe.jeffrey.microscope.core.mcp.tools.traces.TraceAnswers.NotificationsStatus;
import cafe.jeffrey.microscope.core.mcp.tools.traces.TraceAnswers.OperationExport;
import cafe.jeffrey.microscope.core.mcp.tools.traces.TraceAnswers.Operation;
import cafe.jeffrey.microscope.core.mcp.tools.traces.TraceAnswers.OperationFlamegraph;
import cafe.jeffrey.microscope.core.mcp.tools.traces.TraceAnswers.Operations;
import cafe.jeffrey.microscope.core.mcp.tools.traces.TraceAnswers.OperationsStatus;
import cafe.jeffrey.microscope.core.mcp.tools.traces.TraceAnswers.Overview;
import cafe.jeffrey.microscope.core.mcp.tools.traces.TraceAnswers.OverviewStatus;
import cafe.jeffrey.microscope.core.mcp.tools.traces.TraceAnswers.SlowestTraces;
import cafe.jeffrey.microscope.core.mcp.tools.traces.TraceAnswers.SpanFlamegraph;
import cafe.jeffrey.microscope.core.mcp.tools.traces.TraceAnswers.SpanFlamegraphStatus;
import cafe.jeffrey.microscope.core.mcp.tools.traces.TraceAnswers.Trace;
import cafe.jeffrey.microscope.core.mcp.tools.traces.TraceAnswers.TraceExport;
import cafe.jeffrey.microscope.core.mcp.tools.traces.TraceLinks;
import cafe.jeffrey.microscope.core.web.controllers.profile.SpanScopedGraphParameters;
import cafe.jeffrey.microscope.mcp.protocol.McpCursor;
import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.mcp.protocol.ToolExecutionException;
import cafe.jeffrey.microscope.model.SpanInterval;
import cafe.jeffrey.microscope.model.SpanScope;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.profile.common.config.GraphComponents;
import cafe.jeffrey.profile.common.config.GraphParameters;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.TraceManager;
import cafe.jeffrey.profile.manager.model.trace.TraceDetail;
import cafe.jeffrey.profile.manager.model.trace.TraceExportSource;
import cafe.jeffrey.profile.manager.model.trace.TraceNotificationGroupRow;
import cafe.jeffrey.profile.manager.model.trace.TraceOperationRow;
import cafe.jeffrey.profile.manager.model.trace.TraceOperationsPage;
import cafe.jeffrey.profile.manager.model.trace.TraceOverview;
import cafe.jeffrey.profile.manager.model.trace.TraceRow;
import cafe.jeffrey.profile.manager.model.trace.TraceSpanRow;
import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.profile.mcp.McpNextTool;
import cafe.jeffrey.profile.mcp.McpToolCost;
import cafe.jeffrey.profile.mcp.McpToolMeta;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.profile.mcp.McpToolRequirement;
import cafe.jeffrey.profile.mcp.ToolParamBounds;
import cafe.jeffrey.profile.mcp.ToolParamValues;
import cafe.jeffrey.profile.resources.request.GenerateTraceSpanFlamegraphRequest;
import cafe.jeffrey.profile.trace.export.TraceAiMarkdownBuilder;
import cafe.jeffrey.profile.trace.export.TraceOperationAiMarkdownBuilder;
import cafe.jeffrey.provider.profile.api.TraceNotificationListQuery;
import cafe.jeffrey.provider.profile.api.TraceOperationId;
import cafe.jeffrey.provider.profile.api.TraceOperationListQuery;
import cafe.jeffrey.provider.profile.api.TraceOperationSortField;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Jeffrey traces: what the application was asked to do, and what the JVM was doing underneath while it
 * did it.
 * <p>
 * The exports are the interesting part. A trace document carries the span tree together with the GC
 * pauses and waits that crossed it, and a span flamegraph shows the frames sampled while one span was
 * open — questions ordinary tracing cannot answer, because the spans and the profiler samples live in
 * the same recording here. The exports keep their Markdown as the text the agent reads; the record
 * beside it says what it was built from, the next calls, and the Microscope page for the user.
 */
public class TracesMcpTools {

    private static final MicroscopeView OPERATIONS_VIEW = MicroscopeView.TRACES_OPERATIONS;
    private static final String FLAMES_TAB = "flames";
    private static final String SLOWEST_TAB = "slowest";

    private static final int DEFAULT_OPERATIONS_LIMIT = 50;
    private static final int DEFAULT_TRACES_LIMIT = 20;
    private static final int MAX_LIMIT = 1000;

    /**
     * How much of an operation an AI bundle carries. Wider than the UI's twenty, because a reader
     * scrolls and a model does not, and narrow enough to stay inside an agent's context window.
     */
    private static final int AI_EXPORT_SPANS_LIMIT = 40;
    /** Exemplars to name at the end of an operation bundle, as candidates to export individually. */
    private static final int AI_EXPORT_EXEMPLARS_LIMIT = 10;
    /** Notification kinds an operation bundle carries, the most severe first. */
    private static final int AI_EXPORT_NOTIFICATION_KINDS_LIMIT = 25;
    private static final int DEFAULT_NOTIFICATIONS_LIMIT = 50;

    private static final int HEX_RADIX = 16;
    private static final String STATUS_LINE = "status: %s\n%s";

    private static final String NO_TRACES =
            "This profile contains no traces. Traces come from the Jeffrey tracing instrumentation "
                    + "(jeffrey-events / the @Traced agent); a recording without it has none.";
    private static final String NO_NOTIFICATIONS =
            "The application raised no notifications inside any trace of this profile. Notifications "
                    + "are jeffrey.Notification events an instrumented application emits about itself; "
                    + "a recording without them has none to report.";
    private static final String NO_OPERATIONS_MATCHED =
            "No operation matches the given filters; the unfiltered list holds every operation of this profile.";
    private static final String NO_NOTIFICATIONS_MATCHED =
            "No notification matches the given filters; the unfiltered list holds every kind this profile raised.";
    private static final String NO_SELF_TIME =
            "Span %s of trace %s has no self time to graph: its children covered all of it, so selfOnly cut "
                    + "the whole window away.";

    private static final String NO_SUCH_OPERATION =
            "No such operation: %s (kind=%s, eventType=%s). traces_operations lists the operations this "
                    + "profile holds.";
    private static final String NO_SUCH_TRACE =
            "No such trace: %s. traces_slowestTraces and traces_attributeSearch list trace ids.";
    private static final String NO_SUCH_SPAN =
            "No span %s in trace %s. The span ids are in the span tree traces_traceExport returns.";
    private static final String ROW_TOO_LARGE =
            "A single row of this list exceeds the answer size limit; narrow the question.";

    private static final String NOTIFICATIONS_LINK_NOTE =
            "Microscope has no page that lists notifications; this opens the traced operations, which count "
                    + "each operation's notifications";
    private static final String SPAN_LINK_NOTE =
            "opens the trace's span waterfall; the span, the event type and the flags are chosen there";
    private static final String OPERATION_FLAMES_LINK_NOTE =
            "opens the operation's flamegraphs tab, which draws its own panels rather than this event type and flags";

    private static final String OPERATIONS_WHY = "where the wall-clock went, by operation";
    private static final String NOTIFICATIONS_WHY =
            "what the application said went wrong: CRITICAL or HIGH notifications were raised";
    private static final String UNFILTERED_WHY = "the same list without the filters";
    private static final String NEXT_PAGE_WHY = "the next page of this list";
    private static final String OPERATION_EXPORT_WHY =
            "the operation's latency, where its time went by span, and its exemplar traces";
    private static final String SLOWEST_WHY = "the operation's individual traces, slowest first";
    private static final String TRACE_EXPORT_WHY = "this trace span by span, with the JVM underneath it";
    private static final String EXEMPLAR_WHY = "the trace that exemplifies this, span by span";
    private static final String POPULATION_WHY = "whether this trace is typical of its operation or an outlier";
    private static final String SPAN_FRAMES_WHY =
            "the code running inside the span with the most self time: the frames the span tree cannot show";
    private static final String OPERATION_FRAMES_WHY = "what this kind of request spends its time on, across every trace";
    private static final String SPAN_TREE_WHY = "the span tree around this span";
    private static final String SELF_ONLY_WHY = "only the work the span did itself, its children cut out";
    private static final String WHOLE_SPAN_WHY = "the whole span, children included";
    private static final String NO_PLATFORM_SPAN =
            "No span of this trace ran on a platform thread, so no profiler sample can be attributed to it.";

    private final ProfileManager profileManager;
    private final AdvertisedFamilies advertised;

    public TracesMcpTools(ProfileManager profileManager, AdvertisedFamilies advertised) {
        this.profileManager = profileManager;
        this.advertised = advertised;
    }

    @Tool(description = "Returns profile-wide trace totals: how many traces and spans were recorded, "
            + "how many failed, how many notifications the application raised inside them (and how "
            + "many of those were CRITICAL or HIGH), and the latency distribution across all of them "
            + "in nanoseconds - and so whether the profile has traces at all. status NO_TRACES: the "
            + "recording was not made with the tracing instrumentation.")
    @McpOutputSchema(Overview.class)
    @McpToolMeta(cost = McpToolCost.MODERATE)
    public McpToolResult overview() {
        String uiLink = UiLinks.view(profileId(), OPERATIONS_VIEW);
        TraceOverview overview = traceManager().overview();
        if (overview.totalTraces() == 0) {
            return McpToolResult.of(new Overview(OverviewStatus.NO_TRACES, NO_TRACES, profileId(), null,
                    NextSteps.builder(advertised).followUp(), uiLink));
        }

        McpFollowUp followUp = NextSteps.builder(advertised)
                .nextWhen(overview.urgentNotificationCount() > 0,
                        call(FollowUpCalls.TRACES_NOTIFICATIONS).why(NOTIFICATIONS_WHY))
                .next(call(FollowUpCalls.TRACES_OPERATIONS).why(OPERATIONS_WHY))
                .followUp();
        return McpToolResult.of(new Overview(OverviewStatus.OK, null, profileId(), overview, followUp, uiLink));
    }

    @Tool(description = "Returns the operations this recording traced, one row per operation with its "
            + "call count, error count, notification counts and latency percentiles in nanoseconds. "
            + "An operation is a kind of request - 'GET /orders' served over HTTP, say - identified by "
            + "the triple (name, kind, eventType) that every other traces tool takes. Paged: pass "
            + "nextCursor back as cursor, with the same filters, while hasMore is true. status "
            + "NO_TRACES: the profile has no traces; NO_MATCH: none matches the filters.")
    @McpOutputSchema(Operations.class)
    @McpToolMeta(cost = McpToolCost.MODERATE, requires = McpToolRequirement.TRACES)
    public McpToolResult operations(
            @ToolParam(required = false, description = "Optional case-insensitive substring matched against the operation name")
            String search,
            @ToolParam(required = false, description = "Keep only operations that failed at least once")
            Boolean errorsOnly,
            @ToolParam(required = false, description = "Order by one of: TOTAL_TIME, P50, P95, P99, MAX, COUNT, ERRORS, "
                    + "NOTIFICATIONS, NAME. Defaults to TOTAL_TIME, which surfaces where the wall-clock "
                    + "actually went.")
            TraceOperationSortField sort,
            @ToolParam(required = false, description = "Maximum number of operations to return (default "
                    + DEFAULT_OPERATIONS_LIMIT + ", maximum " + MAX_LIMIT + "); fewer when they do not fit the answer")
            @ToolParamBounds(defaultValue = DEFAULT_OPERATIONS_LIMIT, min = 1, max = MAX_LIMIT)
            Integer limit,
            @ToolParam(required = false, description = "The nextCursor of the previous page, with the same filters; "
                    + "omit for the first page")
            String cursor) {

        String searchText = blankToNull(search);
        boolean errors = Boolean.TRUE.equals(errorsOnly);
        TraceOperationSortField order = sort == null ? TraceOperationSortField.TOTAL_TIME : sort;
        int rows = ToolArguments.boundedLimit(limit, DEFAULT_OPERATIONS_LIMIT, MAX_LIMIT);
        McpCursor.Filters filters = McpCursor.Filters.of(
                FollowUpCalls.TRACES_OPERATIONS, profileId(), searchText, errors, order);
        int start = OffsetPaging.offset(cursor, filters);
        String uiLink = UiLinks.view(profileId(), OPERATIONS_VIEW);

        TraceOperationsPage page = traceManager().operations(
                new TraceOperationListQuery(searchText, errors, order, true, rows, start));
        if (page.totalMatching() == 0 && page.operations().isEmpty()) {
            boolean filtered = searchText != null || errors;
            McpFollowUp followUp = NextSteps.builder(advertised)
                    .nextWhen(filtered, call(FollowUpCalls.TRACES_OPERATIONS).why(UNFILTERED_WHY))
                    .followUp();
            return McpToolResult.of(new Operations(
                    filtered ? OperationsStatus.NO_MATCH : OperationsStatus.NO_TRACES,
                    filtered ? NO_OPERATIONS_MATCHED : NO_TRACES,
                    profileId(), List.of(), 0, false, null, followUp, uiLink));
        }

        List<TraceOperationRow> all = page.operations();
        // A total that drifted below the rows actually returned is raised to them, so the page never
        // claims fewer operations than it carries.
        int total = Math.toIntExact(Math.max(page.totalMatching(), (long) start + all.size()));
        Operations answer = FittingPage.largest(all.size(), shown -> {
            List<TraceOperationRow> rowsShown = all.subList(0, shown);
            McpCursor.Next next = OffsetPaging.next(filters, start, shown, start + shown < total);
            NextSteps.Builder steps = NextSteps.builder(advertised);
            if (!rowsShown.isEmpty()) {
                TraceOperationRow first = rowsShown.getFirst();
                steps.next(operationCall(FollowUpCalls.TRACES_OPERATION_EXPORT, first.name(), first.kind(),
                                first.eventType()).why(OPERATION_EXPORT_WHY))
                        .next(operationCall(FollowUpCalls.TRACES_SLOWEST, first.name(), first.kind(),
                                first.eventType()).why(SLOWEST_WHY));
            }
            steps.nextWhen(next.hasMore(), call(FollowUpCalls.TRACES_OPERATIONS)
                    .with(FollowUpCalls.SEARCH, searchText)
                    .with(FollowUpCalls.ERRORS_ONLY, errors)
                    .with(FollowUpCalls.SORT, order)
                    .with(FollowUpCalls.LIMIT, rows)
                    .with(FollowUpCalls.CURSOR, next.nextCursor())
                    .why(NEXT_PAGE_WHY));
            List<Operation> operations = rowsShown.stream()
                    .map(row -> Operation.of(row, operationUrl(row.name(), row.kind(), row.eventType(), null)))
                    .toList();
            return new Operations(OperationsStatus.OK, null, profileId(), operations, total,
                    next.hasMore(), next.nextCursor(), steps.followUp(), uiLink);
        }, FittingPage::fits).orElseThrow(() -> new ToolExecutionException(ROW_TOO_LARGE));
        return McpToolResult.of(answer);
    }

    @Tool(description = "Exports one operation as Markdown for reading: its latency percentiles, where "
            + "its time went broken down by span, and its slowest individual traces named as "
            + "exemplars for traces_traceExport. The Markdown is the text; the structured result "
            + "names the operation, the next calls and uiLink, the operation's page in Microscope. "
            + "An unknown operation is an error naming it.")
    @McpOutputSchema(OperationExport.class)
    @McpToolMeta(maxResultSizeChars = McpToolOutput.MAX_CHARS, cost = McpToolCost.EXPENSIVE,
            requires = McpToolRequirement.TRACES)
    public McpToolResult operationExport(
            @ToolParam(required = true, description = "Operation name, e.g. 'GET /orders'")
            String name,
            @ToolParam(required = true, description = "Span kind of the operation's root, e.g. 'SERVER' or 'CLIENT'")
            @ToolParamValues({"SERVER", "CLIENT", "INTERNAL", "PRODUCER", "CONSUMER"})
            String kind,
            @ToolParam(required = true, description = "Event type that opened the trace, e.g. 'jeffrey.HttpServerExchange'")
            String eventType) {

        TraceOperationId operationId = operationId(name, kind, eventType);
        TraceManager traceManager = traceManager();
        TraceOperationRow operation = requireOperation(operationId);
        List<TraceRow> exemplars = traceManager.slowestTracesOfOperation(operationId, AI_EXPORT_EXEMPLARS_LIMIT);

        String export = new TraceOperationAiMarkdownBuilder(
                operation,
                traceManager.operationSummary(operationId, AI_EXPORT_SPANS_LIMIT),
                traceManager.notifications(
                        TraceNotificationListQuery.ofOperation(operationId, AI_EXPORT_NOTIFICATION_KINDS_LIMIT)),
                exemplars)
                .build();

        NextSteps.Builder steps = NextSteps.builder(advertised);
        if (!exemplars.isEmpty()) {
            steps.next(call(FollowUpCalls.TRACES_TRACE_EXPORT)
                    .with(FollowUpCalls.TRACE_ID, exemplars.getFirst().traceId())
                    .why(EXEMPLAR_WHY));
        }
        Optional<String> onCpu = FollowUpCalls.recordedOnCpuEvent(profileManager);
        McpFollowUp followUp = steps
                .next(operationCall(FollowUpCalls.TRACES_SLOWEST, name, kind, eventType).why(SLOWEST_WHY))
                .nextWhen(onCpu.isPresent(), operationCall(FollowUpCalls.TRACES_OPERATION_FLAMEGRAPH, name, kind, eventType)
                        .with(FollowUpCalls.GRAPH_EVENT_TYPE, onCpu.orElse(null))
                        .why(OPERATION_FRAMES_WHY))
                .guidanceWhen(onCpu.isEmpty(), advertised.hint(AdvertisedFamilies.FLAMEGRAPH, FollowUpCalls.NO_ON_CPU_EVENT))
                .followUp();
        String uiLink = operationUrl(name, kind, eventType, null);
        LinkedOutput.Footed text = LinkedOutput.footed(export, followUp, uiLink, null);
        return McpToolResult.of(text.text(), new OperationExport(profileId(), operationId.name(),
                operationId.kind(), operationId.eventType(), export.length(), text.truncated(), followUp, uiLink));
    }

    @Tool(description = "Returns what the application reported about itself while traces ran: every "
            + "jeffrey.Notification raised inside a trace, grouped by kind, the most severe first. "
            + "Each group carries the type, severity, category, source and message, how many times it "
            + "was raised and in how many distinct traces, when it was first and last raised (UTC "
            + "epoch milliseconds), and a few exemplar trace ids (slowest first) for "
            + "traces_traceExport. Answers 'what went wrong during this recording' without reading "
            + "any timing; traces_overview gives the total. status NO_TRACES, NO_NOTIFICATIONS or "
            + "NO_MATCH says why the list is empty.")
    @McpOutputSchema(Notifications.class)
    @McpToolMeta(cost = McpToolCost.MODERATE, requires = McpToolRequirement.TRACES)
    public McpToolResult notifications(
            @ToolParam(required = false, description = "Keep only one severity")
            @ToolParamValues({"CRITICAL", "HIGH", "MEDIUM", "LOW"})
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
            @ToolParam(required = false, description = "Maximum number of notification kinds to return (default "
                    + DEFAULT_NOTIFICATIONS_LIMIT + ", maximum " + MAX_LIMIT + ")")
            @ToolParamBounds(defaultValue = DEFAULT_NOTIFICATIONS_LIMIT, min = 1, max = MAX_LIMIT)
            Integer limit) {

        TraceOperationId operation = optionalOperationId(name, kind, eventType);
        int rows = ToolArguments.boundedLimit(limit, DEFAULT_NOTIFICATIONS_LIMIT, MAX_LIMIT);
        TraceNotificationListQuery query = new TraceNotificationListQuery(
                severity, type, category, source, search, operation, rows);
        String uiLink = operation == null
                ? UiLinks.view(profileId(), OPERATIONS_VIEW)
                : operationUrl(operation.name(), operation.kind(), operation.eventType(), null);

        List<TraceNotificationGroupRow> groups = traceManager().notifications(query);
        if (groups.isEmpty()) {
            if (query.isFiltered()) {
                McpFollowUp followUp = NextSteps.builder(advertised)
                        .next(call(FollowUpCalls.TRACES_NOTIFICATIONS).why(UNFILTERED_WHY))
                        .followUp();
                return McpToolResult.of(new Notifications(NotificationsStatus.NO_MATCH, NO_NOTIFICATIONS_MATCHED,
                        profileId(), List.of(), null, followUp, uiLink, NOTIFICATIONS_LINK_NOTE));
            }
            boolean traced = traceManager().overview().totalTraces() > 0;
            return McpToolResult.of(new Notifications(
                    traced ? NotificationsStatus.NO_NOTIFICATIONS : NotificationsStatus.NO_TRACES,
                    traced ? NO_NOTIFICATIONS : NO_TRACES,
                    profileId(), List.of(), null, NextSteps.builder(advertised).followUp(), uiLink,
                    NOTIFICATIONS_LINK_NOTE));
        }

        Optional<RecordingSpan> span = RecordingSpan.of(profileManager.info());
        List<NotificationGroup> all = groups.stream().map(group -> group(group, span)).toList();
        Notifications answer = FittingPage.largest(all.size(), shown -> {
            List<NotificationGroup> groupsShown = List.copyOf(all.subList(0, shown));
            NextSteps.Builder steps = NextSteps.builder(advertised);
            groupsShown.stream()
                    .flatMap(group -> group.exemplarTraces().stream())
                    .map(ExemplarTrace::traceId)
                    .findFirst()
                    .ifPresent(traceId -> steps.next(call(FollowUpCalls.TRACES_TRACE_EXPORT)
                            .with(FollowUpCalls.TRACE_ID, traceId)
                            .why(EXEMPLAR_WHY)));
            return new Notifications(NotificationsStatus.OK, null, profileId(), groupsShown,
                    omitted(groups.size(), shown, rows), steps.followUp(), uiLink, NOTIFICATIONS_LINK_NOTE);
        }, FittingPage::fits).orElseThrow(() -> new ToolExecutionException(ROW_TOO_LARGE));
        return McpToolResult.of(answer);
    }

    @Tool(description = "Returns individual traces of one operation, slowest first, with the ids "
            + "traces_traceExport takes and each trace's start as UTC epoch milliseconds. An "
            + "operation this profile does not hold is an error naming it.")
    @McpOutputSchema(SlowestTraces.class)
    @McpToolMeta(cost = McpToolCost.MODERATE, requires = McpToolRequirement.TRACES)
    public McpToolResult slowestTraces(
            @ToolParam(required = true, description = "Operation name, e.g. 'GET /orders'")
            String name,
            @ToolParam(required = true, description = "Span kind of the operation's root, e.g. 'SERVER'")
            @ToolParamValues({"SERVER", "CLIENT", "INTERNAL", "PRODUCER", "CONSUMER"})
            String kind,
            @ToolParam(required = true, description = "Event type that opened the trace")
            String eventType,
            @ToolParam(required = false, description = "Maximum number of traces to return (default "
                    + DEFAULT_TRACES_LIMIT + ", maximum " + MAX_LIMIT + "); fewer when they do not fit the answer")
            @ToolParamBounds(defaultValue = DEFAULT_TRACES_LIMIT, min = 1, max = MAX_LIMIT)
            Integer limit) {

        TraceOperationId operation = operationId(name, kind, eventType);
        int rows = ToolArguments.boundedLimit(limit, DEFAULT_TRACES_LIMIT, MAX_LIMIT);
        List<TraceRow> traces = traceManager().slowestTracesOfOperation(operation, rows);
        // An operation exists only through its traces, so an empty list means the triple names no
        // operation of this profile - a caller mistake to correct, not an empty answer.
        if (traces.isEmpty()) {
            throw new ToolExecutionException(noSuchOperation(operation));
        }

        String uiLink = operationUrl(operation.name(), operation.kind(), operation.eventType(), SLOWEST_TAB);
        List<Trace> all = traces.stream().map(row -> Trace.of(row, traceUrl(row.traceId()))).toList();
        SlowestTraces answer = FittingPage.largest(all.size(), shown -> {
            List<Trace> tracesShown = List.copyOf(all.subList(0, shown));
            NextSteps.Builder steps = NextSteps.builder(advertised);
            if (!tracesShown.isEmpty()) {
                steps.next(call(FollowUpCalls.TRACES_TRACE_EXPORT)
                        .with(FollowUpCalls.TRACE_ID, tracesShown.getFirst().traceId())
                        .why(TRACE_EXPORT_WHY));
            }
            steps.next(operationCall(FollowUpCalls.TRACES_OPERATION_EXPORT, operation.name(), operation.kind(),
                    operation.eventType()).why(POPULATION_WHY));
            return new SlowestTraces(profileId(), operation.name(), operation.kind(), operation.eventType(),
                    tracesShown, omitted(traces.size(), shown, rows), steps.followUp(), uiLink);
        }, FittingPage::fits).orElseThrow(() -> new ToolExecutionException(ROW_TOO_LARGE));
        return McpToolResult.of(answer);
    }

    @Tool(description = "Exports one trace as Markdown for reading: its span tree with self time and "
            + "critical path, the JVM context underneath it (GC pauses, monitor waits, parks), a "
            + "ranked accounting of where its wall-clock went, its I/O shape and its exceptions. "
            + "Answers 'why was this request slow'. The Markdown is the text; the structured result "
            + "names the next calls and uiLink, the trace's span waterfall in Microscope. An unknown "
            + "trace id is an error naming it.")
    @McpOutputSchema(TraceExport.class)
    @McpToolMeta(maxResultSizeChars = McpToolOutput.MAX_CHARS, cost = McpToolCost.EXPENSIVE,
            requires = McpToolRequirement.TRACES)
    public McpToolResult traceExport(
            @ToolParam(required = true, description = "Trace id as a 16-character hex string, from traces_slowestTraces")
            String traceId) {

        long id = parseId(traceId, "traceId");
        TraceExportSource source = traceManager().export(id)
                .orElseThrow(() -> new ToolExecutionException(NO_SUCH_TRACE.formatted(traceId)));
        TraceDetail detail = source.detail();
        String export = new TraceAiMarkdownBuilder(source).build();

        // A sample is matched to a span by thread and window, and the profiler files it under the
        // carrier: a trace that never left its virtual threads has nothing to draw, as the UI says.
        boolean reachable = detail.trace().hasPlatformSpan();
        Optional<String> onCpu = FollowUpCalls.recordedOnCpuEvent(profileManager);
        Optional<TraceSpanRow> busiest = detail.spans().stream()
                .max(Comparator.comparingLong(TraceSpanRow::selfDurationNanos));
        NextSteps.Builder steps = NextSteps.builder(advertised);
        busiest.filter(span -> reachable)
                .flatMap(span -> onCpu.map(type -> call(FollowUpCalls.TRACES_SPAN_FLAMEGRAPH)
                        .with(FollowUpCalls.TRACE_ID, traceId.trim())
                        .with(FollowUpCalls.SPAN_ID, span.spanId())
                        .with(FollowUpCalls.EVENT_TYPE, type)
                        .why(SPAN_FRAMES_WHY)))
                .ifPresent(steps::next);
        steps.guidanceWhen(!reachable, NO_PLATFORM_SPAN)
                .guidanceWhen(reachable && onCpu.isEmpty(),
                        advertised.hint(AdvertisedFamilies.FLAMEGRAPH, FollowUpCalls.NO_ON_CPU_EVENT));
        TraceRow root = detail.trace();
        if (isGiven(root.rootName()) && isGiven(root.rootKind()) && isGiven(root.rootEventType())) {
            steps.next(operationCall(FollowUpCalls.TRACES_OPERATION_EXPORT, root.rootName(), root.rootKind(),
                    root.rootEventType()).why(POPULATION_WHY));
        }
        McpFollowUp followUp = steps.followUp();
        String uiLink = traceUrl(traceId.trim());
        LinkedOutput.Footed text = LinkedOutput.footed(export, followUp, uiLink, null);
        return McpToolResult.of(text.text(), new TraceExport(profileId(), traceId.trim(), export.length(),
                text.truncated(), followUp, uiLink));
    }

    @Tool(description = "Exports, as a Markdown flamegraph, the samples taken while one span was open. "
            + "Answers 'what code was running inside this span', which the span tree alone cannot "
            + "say. The Markdown is the text; uiLink opens the trace's span waterfall, where the "
            + "span's graph is drawn, and uiLinkNote says what the link cannot choose. Ids that name "
            + "no span are an error. status NO_SELF_TIME: selfOnly left nothing, because the span's "
            + "children covered all of its time.")
    @McpOutputSchema(SpanFlamegraph.class)
    @McpToolMeta(maxResultSizeChars = McpToolOutput.MAX_CHARS, cost = McpToolCost.MODERATE,
            requires = McpToolRequirement.TRACES)
    public McpToolResult spanFlamegraphExport(
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

        long trace = parseId(traceId, "traceId");
        long span = parseId(spanId, "spanId");
        Type type = FlamegraphMcpTools.requireEventType(eventType);
        boolean self = Boolean.TRUE.equals(selfOnly);
        boolean perThread = Boolean.TRUE.equals(threadMode);
        String traceHex = traceId.trim();
        String spanHex = spanId.trim();
        String uiLink = traceUrl(traceHex);

        List<SpanInterval> intervals = traceManager().spanIntervals(trace, span, self);
        McpFollowUp followUp;
        if (intervals.isEmpty()) {
            // An empty window with selfOnly can be a real span whose children covered it; without the
            // cut, a span always has its own window, so its absence means the ids name no span.
            if (!self || traceManager().spanIntervals(trace, span, false).isEmpty()) {
                throw new ToolExecutionException(NO_SUCH_SPAN.formatted(spanHex, traceHex));
            }
            followUp = NextSteps.builder(advertised)
                    .next(spanCall(traceHex, spanHex, type, false, threadMode, useWeight).why(WHOLE_SPAN_WHY))
                    .next(call(FollowUpCalls.TRACES_TRACE_EXPORT).with(FollowUpCalls.TRACE_ID, traceHex).why(SPAN_TREE_WHY))
                    .followUp();
            String reason = NO_SELF_TIME.formatted(spanHex, traceHex);
            LinkedOutput.Footed text = LinkedOutput.footed(
                    STATUS_LINE.formatted(SpanFlamegraphStatus.NO_SELF_TIME, reason), followUp, uiLink, SPAN_LINK_NOTE);
            return McpToolResult.of(text.text(), new SpanFlamegraph(SpanFlamegraphStatus.NO_SELF_TIME, reason,
                    profileId(), traceHex, spanHex, type.code(), true, perThread, useWeight, 0, false, followUp,
                    uiLink, SPAN_LINK_NOTE));
        }

        String markdown = exportScoped(SpanScope.of(intervals), type, perThread, useWeight);
        followUp = NextSteps.builder(advertised)
                .next(call(FollowUpCalls.TRACES_TRACE_EXPORT).with(FollowUpCalls.TRACE_ID, traceHex).why(SPAN_TREE_WHY))
                .nextWhen(!self, spanCall(traceHex, spanHex, type, true, threadMode, useWeight).why(SELF_ONLY_WHY))
                .followUp();
        LinkedOutput.Footed text = LinkedOutput.footed(markdown, followUp, uiLink, SPAN_LINK_NOTE);
        return McpToolResult.of(text.text(), new SpanFlamegraph(SpanFlamegraphStatus.OK, null, profileId(),
                traceHex, spanHex, type.code(), self, perThread, useWeight, markdown.length(), text.truncated(),
                followUp, uiLink, SPAN_LINK_NOTE));
    }

    @Tool(description = "Exports, as a Markdown flamegraph, the samples taken while any trace of one "
            + "operation was running. Answers 'what does this kind of request spend its time on' "
            + "across every occurrence, rather than in one exemplar. The Markdown is the text; "
            + "uiLink opens the operation's flamegraphs tab in Microscope, and uiLinkNote says what "
            + "the link cannot choose. An unknown operation is an error naming it.")
    @McpOutputSchema(OperationFlamegraph.class)
    @McpToolMeta(maxResultSizeChars = McpToolOutput.MAX_CHARS, cost = McpToolCost.MODERATE,
            requires = McpToolRequirement.TRACES)
    public McpToolResult operationFlamegraphExport(
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

        TraceOperationId operation = operationId(name, kind, eventType);
        Type type = FlamegraphMcpTools.requireEventType(graphEventType);
        requireOperation(operation);
        boolean perThread = Boolean.TRUE.equals(threadMode);

        String markdown = exportScoped(new SpanScope.Operation(operation.name(), operation.kind(),
                operation.eventType()), type, perThread, useWeight);
        McpFollowUp followUp = NextSteps.builder(advertised)
                .next(operationCall(FollowUpCalls.TRACES_SLOWEST, name, kind, eventType).why(SLOWEST_WHY))
                .next(operationCall(FollowUpCalls.TRACES_OPERATION_EXPORT, name, kind, eventType)
                        .why(OPERATION_EXPORT_WHY))
                .followUp();
        String uiLink = operationUrl(operation.name(), operation.kind(), operation.eventType(), FLAMES_TAB);
        LinkedOutput.Footed text = LinkedOutput.footed(markdown, followUp, uiLink, OPERATION_FLAMES_LINK_NOTE);
        return McpToolResult.of(text.text(), new OperationFlamegraph(profileId(), operation.name(),
                operation.kind(), operation.eventType(), type.code(), perThread, useWeight, markdown.length(),
                text.truncated(), followUp, uiLink, OPERATION_FLAMES_LINK_NOTE));
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
    private String exportScoped(SpanScope scope, Type type, boolean threadMode, Boolean useWeight) {
        GenerateTraceSpanFlamegraphRequest request = new GenerateTraceSpanFlamegraphRequest(
                false,
                type,
                threadMode,
                useWeight,
                false,
                false,
                false,
                GraphComponents.FLAMEGRAPH_ONLY);

        GraphParameters params = SpanScopedGraphParameters.of(profileManager.info(), request, scope);
        return profileManager.flamegraphManager().generateAiExport(params);
    }

    private TraceOperationRow requireOperation(TraceOperationId operation) {
        return traceManager().operation(operation)
                .orElseThrow(() -> new ToolExecutionException(noSuchOperation(operation)));
    }

    private static String noSuchOperation(TraceOperationId operation) {
        return NO_SUCH_OPERATION.formatted(operation.name(), operation.kind(), operation.eventType());
    }

    /**
     * One notification kind, its offsets from the recording start placed on the epoch clock and each
     * exemplar trace linked to its waterfall.
     */
    private NotificationGroup group(TraceNotificationGroupRow row, Optional<RecordingSpan> span) {
        return new NotificationGroup(row.type(), row.severity(), row.category(), row.source(), row.message(),
                row.count(), row.traceCount(),
                span.map(recording -> recording.epochAt(row.firstMillisFromBeginning())).orElse(null),
                span.map(recording -> recording.epochAt(row.lastMillisFromBeginning())).orElse(null),
                row.exemplarTraceIds().stream()
                        .map(traceId -> new ExemplarTrace(traceId, traceUrl(traceId)))
                        .toList());
    }

    /**
     * What a list capped at {@code limit} and cut to fit left out: exact while the engine returned
     * fewer than asked, unknowable once it returned as many, since more may exist behind the cap.
     */
    private static Integer omitted(int returned, int shown, int limit) {
        return returned >= limit ? null : returned - shown;
    }

    private McpNextTool.Call spanCall(
            String traceId, String spanId, Type type, boolean selfOnly, Boolean threadMode, Boolean useWeight) {

        McpNextTool.Call call = call(FollowUpCalls.TRACES_SPAN_FLAMEGRAPH)
                .with(FollowUpCalls.TRACE_ID, traceId)
                .with(FollowUpCalls.SPAN_ID, spanId)
                .with(FollowUpCalls.EVENT_TYPE, type.code());
        if (selfOnly) {
            call.with(FollowUpCalls.SELF_ONLY, true);
        }
        if (threadMode != null) {
            call.with(FollowUpCalls.THREAD_MODE, threadMode);
        }
        if (useWeight != null) {
            call.with(FollowUpCalls.USE_WEIGHT, useWeight);
        }
        return call;
    }

    private McpNextTool.Call operationCall(String tool, String name, String kind, String eventType) {
        return call(tool)
                .with(FollowUpCalls.NAME, name)
                .with(FollowUpCalls.KIND, kind)
                .with(FollowUpCalls.EVENT_TYPE, eventType);
    }

    private McpNextTool.Call call(String tool) {
        return NextCalls.to(tool).with(FollowUpCalls.PROFILE_ID, profileId());
    }

    private String operationUrl(String name, String kind, String eventType, String tab) {
        return TraceLinks.operation(profileId(), name, kind, eventType, tab);
    }

    private String traceUrl(String traceId) {
        return TraceLinks.trace(profileId(), traceId);
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

    private static String blankToNull(String value) {
        return isGiven(value) ? value : null;
    }

    private static TraceOperationId operationId(String name, String kind, String eventType) {
        return new TraceOperationId(
                requireText(name, "name"),
                requireText(kind, "kind"),
                requireText(eventType, "eventType"));
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
