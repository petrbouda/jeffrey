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

package cafe.jeffrey.microscope.core.web.controllers.profile;

import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.ai.trace.TraceAiMarkdownBuilder;
import cafe.jeffrey.profile.ai.trace.TraceOperationAiMarkdownBuilder;
import cafe.jeffrey.profile.common.config.GraphParameters;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.TraceManager;
import cafe.jeffrey.profile.manager.model.trace.TraceContext;
import cafe.jeffrey.profile.manager.model.trace.TraceDetail;
import cafe.jeffrey.profile.manager.model.trace.TraceOperationRow;
import cafe.jeffrey.profile.manager.model.trace.TraceOperationSummary;
import cafe.jeffrey.profile.manager.model.trace.TraceOperationsPage;
import cafe.jeffrey.profile.manager.model.trace.TraceOverview;
import cafe.jeffrey.profile.model.FlamegraphPanel;
import cafe.jeffrey.profile.panel.JfrFlamegraphPanelProvider;
import cafe.jeffrey.profile.panel.PanelContext;
import cafe.jeffrey.profile.manager.model.trace.TraceRow;
import cafe.jeffrey.profile.manager.model.trace.TraceSpanEvents;
import cafe.jeffrey.profile.manager.model.trace.TraceTimelineBucket;
import cafe.jeffrey.profile.manager.model.trace.TracesPage;
import cafe.jeffrey.profile.resources.request.GenerateTraceOperationFlamegraphRequest;
import cafe.jeffrey.profile.resources.request.GenerateTraceSpanFlamegraphRequest;
import cafe.jeffrey.profile.resources.request.SpanFlamegraphOptions;
import cafe.jeffrey.provider.profile.api.TraceListQuery;
import cafe.jeffrey.provider.profile.api.TraceOperationId;
import cafe.jeffrey.provider.profile.api.TraceOperationListQuery;
import cafe.jeffrey.provider.profile.api.TraceOperationSortField;
import cafe.jeffrey.provider.profile.api.TraceSortField;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.model.SpanInterval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Serves the trace views: the list of traces, one trace's span tree, and what the JVM was doing
 * inside a single span.
 * <p>
 * Trace and span ids travel as 16-char hex strings, because a 64-bit id exceeds JavaScript's safe
 * integer range and would be silently rounded as a JSON number.
 * <p>
 * Kept independent of the async-profiler span feature. {@code profiler.Span} is a flat, per-thread
 * latency interval selected by tag; a trace span is a node in a tree. They answer different
 * questions and share only the interval-scoped flamegraph machinery, which lives in
 * {@link SpanScopedGraphParameters} so neither feature depends on the other.
 */
@RestController
@RequestMapping("/api/internal/profiles/{profileId}/traces")
public class TracesController {

    private static final Logger LOG = LoggerFactory.getLogger(TracesController.class);

    private static final String DEFAULT_TRACES_LIMIT = "100";
    private static final String DEFAULT_OPERATIONS_LIMIT = "100";
    private static final String DEFAULT_OPERATION_TRACES_LIMIT = "1000";
    /** Enough span names to see where the time goes; the tail of a long list is never read. */
    private static final String DEFAULT_OPERATION_SPANS_LIMIT = "20";
    /** The most rows any of these lists can usefully render, and the ceiling a caller cannot raise. */
    private static final int MAX_LIMIT = 10_000;
    /**
     * Markdown, because the export is a document rather than a payload.
     * <p>
     * The charset is explicit: both documents are prose containing em dashes and typographic
     * punctuation, and a string converter that falls back to ISO-8859-1 turns every one of them into
     * a replacement character. Stating UTF-8 costs nothing and removes the question.
     */
    private static final String MARKDOWN_MEDIA_TYPE = "text/markdown;charset=UTF-8";
    /**
     * How much of an operation an AI bundle carries. Wider than the UI's twenty, because a reader
     * scrolls and a model does not, and narrow enough that the document still fits a chat window.
     */
    private static final int AI_EXPORT_SPANS_LIMIT = 40;
    /** Exemplars to name at the end of an operation bundle, as candidates to export individually. */
    private static final int AI_EXPORT_EXEMPLARS_LIMIT = 10;
    /** Slowest-first and busiest-first: what each list showed before it could be sorted at all. */
    private static final String DEFAULT_TRACE_SORT = "DURATION";
    private static final String DEFAULT_OPERATION_SORT = "TOTAL_TIME";
    /** Enough points to see where a recording got busy, few enough to stay a strip rather than a chart. */
    private static final String DEFAULT_TIMELINE_BUCKETS = "60";
    private static final int MAX_TIMELINE_BUCKETS = 500;

    private final ProfileManagerResolver resolver;
    private final JfrFlamegraphPanelProvider panelProvider;

    public TracesController(ProfileManagerResolver resolver, JfrFlamegraphPanelProvider panelProvider) {
        this.resolver = resolver;
        this.panelProvider = panelProvider;
    }

    /**
     * The trace list, narrowed and paged by the caller.
     * <p>
     * Every parameter has the default the list had before any of them existed, so a caller that
     * sends none gets the same slowest-first page it always did.
     */
    @GetMapping
    public TracesPage traces(
            @PathVariable("profileId") String profileId,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "errorsOnly", defaultValue = "false") boolean errorsOnly,
            @RequestParam(value = "minDurationNanos", defaultValue = "0") long minDurationNanos,
            @RequestParam(value = "sort", defaultValue = DEFAULT_TRACE_SORT) TraceSortField sort,
            @RequestParam(value = "desc", defaultValue = "true") boolean descending,
            @RequestParam(value = "limit", defaultValue = DEFAULT_TRACES_LIMIT) int limit,
            @RequestParam(value = "offset", defaultValue = "0") int offset) {
        LOG.debug("Listing traces: profile_id={} search={} errors_only={} sort={} limit={} offset={}",
                profileId, search, errorsOnly, sort, limit, offset);

        TraceListQuery query = new TraceListQuery(
                search,
                errorsOnly,
                Math.max(0, minDurationNanos),
                sort,
                descending,
                boundedLimit(limit),
                boundedOffset(offset));
        return resolver.resolve(profileId).traceManager().traces(query);
    }

    /**
     * How traces were spread over the recording, for the density strip above the list. Aggregated
     * server-side because the list itself is capped — bucketing the slowest hundred would draw the
     * shape of the tail and label it the shape of the profile.
     */
    @GetMapping("/timeline")
    public List<TraceTimelineBucket> timeline(
            @PathVariable("profileId") String profileId,
            @RequestParam(value = "buckets", defaultValue = DEFAULT_TIMELINE_BUCKETS) int buckets) {
        LOG.debug("Bucketing traces over the recording: profile_id={} buckets={}", profileId, buckets);
        return resolver.resolve(profileId).traceManager()
                .timeline(Math.clamp(buckets, 1, MAX_TIMELINE_BUCKETS));
    }

    /**
     * Profile-wide totals for the summary above the trace list. Spring prefers the literal path over
     * the {@code /{traceId}} template regardless of declaration order; the two are grouped here for
     * a reader, not for the dispatcher.
     */
    @GetMapping("/overview")
    public TraceOverview overview(@PathVariable("profileId") String profileId) {
        LOG.debug("Reading trace overview: profile_id={}", profileId);
        return resolver.resolve(profileId).traceManager().overview();
    }

    @GetMapping("/operations")
    public TraceOperationsPage operations(
            @PathVariable("profileId") String profileId,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "errorsOnly", defaultValue = "false") boolean errorsOnly,
            @RequestParam(value = "sort", defaultValue = DEFAULT_OPERATION_SORT)
            TraceOperationSortField sort,
            @RequestParam(value = "desc", defaultValue = "true") boolean descending,
            @RequestParam(value = "limit", defaultValue = DEFAULT_OPERATIONS_LIMIT) int limit,
            @RequestParam(value = "offset", defaultValue = "0") int offset) {
        LOG.debug("Aggregating trace operations: profile_id={} search={} errors_only={} sort={} limit={} offset={}",
                profileId, search, errorsOnly, sort, limit, offset);

        TraceOperationListQuery query = new TraceOperationListQuery(
                search, errorsOnly, sort, descending, boundedLimit(limit), boundedOffset(offset));
        return resolver.resolve(profileId).traceManager().operations(query);
    }

    /**
     * The traces of one type. Feeds both the timeline and the slowest list of the operation
     * drill-down, which is why it is ordered by time rather than by duration.
     * <p>
     * The name travels as a query parameter, not a path segment: operation names contain slashes
     * and braces ({@code GET /api/internal/profiles/{profileId}/heap/instances}).
     */
    @GetMapping("/operation/traces")
    public List<TraceRow> operationTraces(
            @PathVariable("profileId") String profileId,
            @RequestParam("name") String name,
            @RequestParam("kind") String kind,
            @RequestParam("eventType") String eventType,
            @RequestParam(value = "limit", defaultValue = DEFAULT_OPERATION_TRACES_LIMIT) int limit) {
        LOG.debug("Listing traces of an operation: profile_id={} name={} kind={} event_type={} limit={}",
                profileId, name, kind, eventType, limit);
        return resolver.resolve(profileId).traceManager()
                .tracesOfOperation(new TraceOperationId(name, kind, eventType), boundedLimit(limit));
    }

    /**
     * The parts of an operation's summary that cannot be worked out from the trace list: its span
     * breakdown and its thread split. Everything else on that page is arithmetic over the traces
     * the caller already fetched, so it is not repeated here.
     */
    @GetMapping("/operation/summary")
    public TraceOperationSummary operationSummary(
            @PathVariable("profileId") String profileId,
            @RequestParam("name") String name,
            @RequestParam("kind") String kind,
            @RequestParam("eventType") String eventType,
            @RequestParam(value = "spanLimit", defaultValue = DEFAULT_OPERATION_SPANS_LIMIT) int spanLimit) {
        LOG.debug("Summarising an operation: profile_id={} name={} kind={} event_type={} span_limit={}",
                profileId, name, kind, eventType, spanLimit);
        return resolver.resolve(profileId).traceManager()
                .operationSummary(new TraceOperationId(name, kind, eventType), boundedLimit(spanLimit));
    }

    /**
     * One operation rendered as Markdown for an AI to read — percentiles, the span breakdown in both
     * accountings, and slowest exemplars, with a preamble that defines every term it uses.
     * <p>
     * Placed before {@code /{traceId}} so the literal path is matched as one rather than being read
     * as a trace id.
     */
    @GetMapping(value = "/operation/ai-export", produces = MARKDOWN_MEDIA_TYPE)
    public String operationAiExport(
            @PathVariable("profileId") String profileId,
            @RequestParam("name") String name,
            @RequestParam("kind") String kind,
            @RequestParam("eventType") String eventType) {
        LOG.debug("Exporting an operation for AI: profile_id={} name={} kind={} event_type={}",
                profileId, name, kind, eventType);

        TraceOperationId operationId = new TraceOperationId(name, kind, eventType);
        TraceManager traceManager = resolver.resolve(profileId).traceManager();
        TraceOperationRow operation = traceManager.operation(operationId)
                .orElseThrow(() -> Exceptions.resourceNotFound("Operation not found: " + name));

        return new TraceOperationAiMarkdownBuilder(
                operation,
                traceManager.operationSummary(operationId, AI_EXPORT_SPANS_LIMIT),
                traceManager.tracesOfOperation(operationId, AI_EXPORT_EXEMPLARS_LIMIT))
                .build();
    }

    /**
     * One trace rendered as Markdown for an AI to read.
     * <p>
     * The context is fetched alongside the trace rather than left out: without the pauses and waits
     * this is an ordinary tracing export, and the JVM's own events are the whole reason Jeffrey's
     * version of this document is worth reading.
     */
    @GetMapping(value = "/{traceId}/ai-export", produces = MARKDOWN_MEDIA_TYPE)
    public String traceAiExport(
            @PathVariable("profileId") String profileId,
            @PathVariable("traceId") String traceId) {
        LOG.debug("Exporting a trace for AI: profile_id={} trace_id={}", profileId, traceId);

        long id = parseId(traceId);
        TraceManager traceManager = resolver.resolve(profileId).traceManager();
        TraceDetail detail = traceManager.trace(id)
                .orElseThrow(() -> Exceptions.resourceNotFound("Trace not found: " + traceId));

        return new TraceAiMarkdownBuilder(detail, traceManager.context(id)).build();
    }

    @GetMapping("/{traceId}")
    public TraceDetail trace(
            @PathVariable("profileId") String profileId,
            @PathVariable("traceId") String traceId) {
        LOG.debug("Loading trace: profile_id={} trace_id={}", profileId, traceId);
        return resolver.resolve(profileId).traceManager()
                .trace(parseId(traceId))
                .orElseThrow(() -> Exceptions.resourceNotFound("Trace not found: " + traceId));
    }

    /**
     * What the JVM was doing to this trace: the stop-the-world pauses that crossed it, what each
     * span waited on, and a ranked summary of where its wall-clock time went.
     * <p>
     * Separate from {@code GET /{traceId}} because it is a second question about the same trace, and
     * a slower one — the pauses come from a scan of the events table rather than from the derived
     * span tables. Keeping them apart lets the waterfall draw immediately and the context arrive
     * over it.
     */
    @GetMapping("/{traceId}/context")
    public TraceContext context(
            @PathVariable("profileId") String profileId,
            @PathVariable("traceId") String traceId) {
        LOG.debug("Reading trace context: profile_id={} trace_id={}", profileId, traceId);
        return resolver.resolve(profileId).traceManager().context(parseId(traceId));
    }

    /**
     * The events that ran on the span's thread while it was open — the "what was the JVM doing in
     * here" breakdown. Reuses the async-profiler span machinery, which already answers exactly this
     * question for a (thread, window) pair.
     */
    @GetMapping("/{traceId}/spans/{spanId}/events")
    public TraceSpanEvents spanEvents(
            @PathVariable("profileId") String profileId,
            @PathVariable("traceId") String traceId,
            @PathVariable("spanId") String spanId) {
        LOG.debug("Listing events inside a span: profile_id={} trace_id={} span_id={}",
                profileId, traceId, spanId);
        return resolver.resolve(profileId).traceManager()
                .eventsInSpan(parseId(traceId), parseId(spanId));
    }

    /**
     * The flamegraph cards available for one span — which event types actually recorded anything
     * inside it, with the real counts, so the drill-down offers only graphs that exist.
     */
    @GetMapping("/{traceId}/spans/{spanId}/panels")
    public List<FlamegraphPanel> spanPanels(
            @PathVariable("profileId") String profileId,
            @PathVariable("traceId") String traceId,
            @PathVariable("spanId") String spanId,
            @RequestParam(value = "selfOnly", defaultValue = "false") boolean selfOnly) {
        LOG.debug("Building span-scoped flamegraph panels: profile_id={} trace_id={} span_id={} self_only={}",
                profileId, traceId, spanId, selfOnly);
        ProfileManager profileManager = resolver.resolve(profileId);

        return panelsFor(profileManager, profileManager.traceManager()
                .spanIntervals(parseId(traceId), parseId(spanId), selfOnly));
    }

    /**
     * A flamegraph of the samples taken while one span was running — the whole point of keeping
     * traces in the same recording as the profile.
     */
    @PostMapping(value = "/{traceId}/spans/{spanId}/flamegraph",
            produces = ProfileMediaTypes.PROTOBUF)
    public byte[] spanFlamegraph(
            @PathVariable("profileId") String profileId,
            @PathVariable("traceId") String traceId,
            @PathVariable("spanId") String spanId,
            @RequestBody GenerateTraceSpanFlamegraphRequest request) {
        LOG.debug("Generating span flamegraph: profile_id={} trace_id={} span_id={} self_only={}",
                profileId, traceId, spanId, request.selfOnly());
        ProfileManager profileManager = resolver.resolve(profileId);

        List<SpanInterval> intervals = profileManager.traceManager()
                .spanIntervals(parseId(traceId), parseId(spanId), request.selfOnly());
        return flamegraphFor(profileManager, request, intervals,
                "Span has no samples to show: " + spanId);
    }

    /**
     * Which event types recorded samples inside the traces of one type, with their real counts, so
     * the drill-down offers only flamegraphs that exist.
     */
    @GetMapping("/operation/panels")
    public List<FlamegraphPanel> operationPanels(
            @PathVariable("profileId") String profileId,
            @RequestParam("name") String name,
            @RequestParam("kind") String kind,
            @RequestParam("eventType") String eventType) {
        LOG.debug("Building operation-scoped flamegraph panels: profile_id={} name={} kind={} event_type={}",
                profileId, name, kind, eventType);
        ProfileManager profileManager = resolver.resolve(profileId);

        return panelsFor(profileManager, profileManager.traceManager()
                .operationIntervals(new TraceOperationId(name, kind, eventType)));
    }

    /**
     * A flamegraph of the samples taken while any trace of one type was running — "what does this
     * kind of request spend its time on", as opposed to the single-span graph next door.
     */
    @PostMapping(value = "/operation/flamegraph", produces = ProfileMediaTypes.PROTOBUF)
    public byte[] operationFlamegraph(
            @PathVariable("profileId") String profileId,
            @RequestBody GenerateTraceOperationFlamegraphRequest request) {
        LOG.debug("Generating operation flamegraph: profile_id={} name={} kind={} event_type={}",
                profileId, request.name(), request.kind(), request.rootEventType());
        ProfileManager profileManager = resolver.resolve(profileId);

        List<SpanInterval> intervals = profileManager.traceManager()
                .operationIntervals(request.operationId());
        return flamegraphFor(profileManager, request, intervals,
                "Operation has no samples to show: " + request.name());
    }

    /**
     * The flamegraph cards for a set of intervals, or none when the scope covers nothing. A scope
     * with no intervals is a normal answer here — the caller is asking what is available.
     */
    private List<FlamegraphPanel> panelsFor(ProfileManager profileManager, List<SpanInterval> intervals) {
        if (intervals.isEmpty()) {
            return List.of();
        }
        return panelProvider.panels(
                profileManager.flamegraphManager().eventSummaries(intervals), PanelContext.PRIMARY);
    }

    /**
     * A flamegraph over a set of intervals. Unlike {@link #panelsFor}, an empty scope is a 404: the
     * caller asked for a specific graph, and there is no such graph to send.
     */
    private static byte[] flamegraphFor(
            ProfileManager profileManager,
            SpanFlamegraphOptions request,
            List<SpanInterval> intervals,
            String notFoundMessage) {

        if (intervals.isEmpty()) {
            throw Exceptions.resourceNotFound(notFoundMessage);
        }
        GraphParameters params = SpanScopedGraphParameters.of(profileManager.info(), request, intervals);
        return profileManager.flamegraphManager().generate(params);
    }

    /**
     * Keeps a caller-supplied row cap inside what the page can use. A limit is a page size, not a
     * request for the whole table: {@code ?limit=100000000} was previously passed to SQL verbatim.
     */
    private static int boundedLimit(int limit) {
        if (limit < 1) {
            return 1;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    /**
     * Keeps a caller-supplied page offset inside the same ceiling the page size has. A negative
     * offset is not a page, and an unbounded one is a request for the database to count past the
     * end of the table before returning nothing.
     */
    private static int boundedOffset(int offset) {
        return Math.clamp(offset, 0, MAX_LIMIT);
    }

    /**
     * Ids arrive as unsigned 16-char hex, so the full 64-bit range round-trips — including the half
     * of it that is negative as a signed {@code long}.
     */
    private static long parseId(String hex) {
        try {
            return Long.parseUnsignedLong(hex, 16);
        } catch (NumberFormatException e) {
            throw Exceptions.resourceNotFound("Malformed id: " + hex);
        }
    }
}
