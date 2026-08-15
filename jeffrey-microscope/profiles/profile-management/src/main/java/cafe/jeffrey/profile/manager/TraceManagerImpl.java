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

package cafe.jeffrey.profile.manager;

import cafe.jeffrey.profile.manager.model.trace.EventFieldRow;
import cafe.jeffrey.profile.manager.model.trace.TraceContext;
import cafe.jeffrey.profile.manager.model.trace.TraceContextSlice;
import cafe.jeffrey.profile.manager.model.trace.TraceDetail;
import cafe.jeffrey.profile.manager.model.trace.TracePause;
import cafe.jeffrey.profile.manager.model.trace.TraceEventRow;
import cafe.jeffrey.profile.manager.model.trace.TraceOperationRow;
import cafe.jeffrey.profile.manager.model.trace.TraceOperationSpanRow;
import cafe.jeffrey.profile.manager.model.trace.TraceOperationSummary;
import cafe.jeffrey.profile.manager.model.trace.TraceOperationThreads;
import cafe.jeffrey.profile.manager.model.trace.TraceOperationsPage;
import cafe.jeffrey.profile.manager.model.trace.TraceOverview;
import cafe.jeffrey.profile.manager.model.trace.TraceRow;
import cafe.jeffrey.profile.manager.model.trace.TraceSpanEvents;
import cafe.jeffrey.profile.manager.model.trace.TraceSpanRow;
import cafe.jeffrey.profile.manager.model.trace.TraceTimelineBucket;
import cafe.jeffrey.profile.manager.model.trace.TracesPage;
import cafe.jeffrey.provider.profile.api.EventFieldRecord;
import cafe.jeffrey.provider.profile.api.ThreadWindowEventsPage;
import cafe.jeffrey.provider.profile.api.TraceListQuery;
import cafe.jeffrey.provider.profile.api.TraceOperationId;
import cafe.jeffrey.provider.profile.api.TraceOperationListQuery;
import cafe.jeffrey.provider.profile.api.TraceOperationPage;
import cafe.jeffrey.provider.profile.api.TraceOperationSortField;
import cafe.jeffrey.provider.profile.api.TraceOperationThreadsRecord;
import cafe.jeffrey.provider.profile.api.TraceOverviewRecord;
import cafe.jeffrey.provider.profile.api.TracePage;
import cafe.jeffrey.provider.profile.api.TraceRepository;
import cafe.jeffrey.provider.profile.api.TraceSpanContextRecord;
import cafe.jeffrey.provider.profile.api.TraceSpanRecord;
import cafe.jeffrey.provider.profile.api.TraceSummaryRecord;
import cafe.jeffrey.shared.common.model.SpanInterval;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Turns the flat span rows the repository returns into the tree the waterfall draws.
 * <p>
 * The assembly lives here rather than in SQL or in the browser because the same interval arithmetic
 * feeds the span-scoped flamegraph, which has to run server-side anyway.
 * <p>
 * Span arithmetic runs in microseconds, the resolution the stored timestamp carries. Milliseconds
 * are too coarse for it: a span is routinely shorter than one, so rounding its bounds to a
 * millisecond makes sub-millisecond children cost their parent nothing and puts spans that ran one
 * after the other on the same instant. The two places that genuinely are millisecond domains — the
 * sample filter behind {@link SpanInterval} and the event drill-down, both of which run on the
 * events table's millisecond timeline — convert at their own boundary and nowhere earlier.
 */
public class TraceManagerImpl implements TraceManager {

    private static final long NANOS_PER_MICRO = 1_000L;
    private static final long MICROS_PER_MILLI = 1_000L;
    /**
     * How far down the name-filtered list {@link #operation} will look before giving up. Reaching
     * this would take ten thousand operations whose names all contain the one being asked for, which
     * is not a shape a real application produces.
     */
    private static final int OPERATION_LOOKUP_LIMIT = 10_000;

    private final TraceRepository traceRepository;

    public TraceManagerImpl(TraceRepository traceRepository) {
        this.traceRepository = traceRepository;
    }

    @Override
    public TracesPage traces(TraceListQuery query) {
        TracePage page = traceRepository.traces(query);
        return new TracesPage(
                page.traces().stream()
                        .map(TraceManagerImpl::toRow)
                        .toList(),
                page.totalMatching());
    }

    @Override
    public List<TraceTimelineBucket> timeline(int buckets) {
        return traceRepository.timeline(buckets).stream()
                .map(bucket -> new TraceTimelineBucket(
                        bucket.fromMillisFromBeginning(),
                        bucket.count(),
                        bucket.errorCount(),
                        bucket.maxDurationNanos()))
                .toList();
    }

    @Override
    public List<TraceRow> tracesOfOperation(TraceOperationId operation, int limit) {
        return traceRepository.tracesOfOperation(operation, limit).stream()
                .map(TraceManagerImpl::toRow)
                .toList();
    }

    @Override
    public TraceOverview overview() {
        TraceOverviewRecord overview = traceRepository.overview();
        return new TraceOverview(
                overview.totalTraces(),
                overview.totalSpans(),
                overview.errorTraces(),
                overview.errorSpans(),
                overview.avgNanos(),
                overview.p95Nanos(),
                overview.p99Nanos(),
                overview.maxNanos(),
                overview.totalNanos(),
                overview.distinctOperations());
    }

    @Override
    public Optional<TraceDetail> trace(long traceId) {
        List<TraceSpanRecord> spans = traceRepository.spansOf(traceId);
        if (spans.isEmpty()) {
            return Optional.empty();
        }
        // The stored header rather than one rebuilt from the spans: the derivation already settled
        // the root and the duration in nanoseconds, and recomputing them here from microsecond-
        // truncated span bounds made the same trace report one duration in the list and a shorter
        // one in its own detail.
        return traceRepository.summaryOf(traceId)
                .map(summary -> new TraceDetail(toRow(summary), assemble(spans), eventFieldsOf(spans)));
    }

    /**
     * The field metadata for the event types this trace's spans came from, grouped by type.
     * <p>
     * Looked up for the types actually present rather than for every traced type, so a trace of one
     * HTTP request does not carry the schema of six JDBC events the UI will never draw.
     */
    private Map<String, List<EventFieldRow>> eventFieldsOf(List<TraceSpanRecord> spans) {
        List<String> eventTypes = spans.stream()
                .map(TraceSpanRecord::eventType)
                .distinct()
                .toList();

        return traceRepository.eventFieldsOf(eventTypes).stream()
                .collect(Collectors.groupingBy(
                        EventFieldRecord::eventType,
                        Collectors.mapping(
                                field -> new EventFieldRow(
                                        field.field(),
                                        field.label(),
                                        field.description(),
                                        field.contentType()),
                                Collectors.toList())));
    }

    /**
     * What the JVM was doing to this trace: the pauses that crossed it, what each span waited on,
     * and the ranked summary of where its time went.
     * <p>
     * The trace's own window comes from its spans rather than from its header, so a pause is looked
     * for over exactly the stretch the waterfall draws — a child that outlived its parent widens
     * both, and the band would otherwise stop short of the bar it explains.
     */
    @Override
    public TraceContext context(long traceId) {
        List<TraceSpanRecord> spans = traceRepository.spansOf(traceId);
        if (spans.isEmpty()) {
            return TraceContext.EMPTY;
        }

        long from = Long.MAX_VALUE;
        long to = Long.MIN_VALUE;
        for (TraceSpanRecord span : spans) {
            from = Math.min(from, span.startEpochMicros());
            to = Math.max(to, endMicrosOf(span));
        }

        List<TracePause> pauses = traceRepository.pausesInWindow(from, to).stream()
                .map(pause -> new TracePause(
                        pause.category().name(),
                        pause.label(),
                        pause.fromEpochMicros(),
                        pause.durationNanos()))
                .toList();

        List<TraceSpanContextRecord> waits = traceRepository.spanContext(traceId);
        Map<String, List<TraceContextSlice>> spanWaits = waits.stream()
                .collect(Collectors.groupingBy(
                        wait -> toHex(wait.spanId()),
                        Collectors.collectingAndThen(
                                Collectors.toList(), TraceManagerImpl::toSlices)));

        return new TraceContext(pauses, spanWaits, summarise(spans, pauses, waits, from, to));
    }

    private static List<TraceContextSlice> toSlices(List<TraceSpanContextRecord> waits) {
        return waits.stream()
                .map(wait -> new TraceContextSlice(
                        wait.category().name(), wait.totalNanos(), wait.occurrences()))
                .sorted(Comparator.comparingLong(TraceContextSlice::totalNanos).reversed())
                .toList();
    }

    /**
     * Where the trace's wall-clock time went, ranked, with the remainder named as the code's own
     * work.
     * <p>
     * The denominator is the trace's own window, not the sum of its spans: spans nest, so summing
     * them counts the same instant once per level of the tree and would put the total far past the
     * time that actually elapsed.
     * <p>
     * Pauses are clipped to that window before being counted — a collection that began before the
     * trace only cost it the stretch they shared — and the waiting is taken as recorded, since a
     * thread waits on one thing at a time and the per-span rows are already disjoint. The residual
     * is floored at zero: the two sources are measured independently, and a pause that overlapped a
     * lock wait can in principle push the accounted total past the window.
     */
    private static List<TraceContextSlice> summarise(
            List<TraceSpanRecord> spans,
            List<TracePause> pauses,
            List<TraceSpanContextRecord> waits,
            long fromMicros,
            long toMicros) {

        Map<String, long[]> totals = new LinkedHashMap<>();
        for (TracePause pause : pauses) {
            long overlapMicros = Math.max(0,
                    Math.min(toMicros, pause.startEpochMicros() + pause.durationNanos() / NANOS_PER_MICRO)
                            - Math.max(fromMicros, pause.startEpochMicros()));
            long[] slot = totals.computeIfAbsent(pause.category(), _ -> new long[2]);
            slot[0] += overlapMicros * NANOS_PER_MICRO;
            slot[1]++;
        }
        for (TraceSpanContextRecord wait : waits) {
            long[] slot = totals.computeIfAbsent(wait.category().name(), _ -> new long[2]);
            slot[0] += wait.totalNanos();
            slot[1] += wait.occurrences();
        }

        List<TraceContextSlice> slices = new ArrayList<>(totals.entrySet().stream()
                .map(entry -> new TraceContextSlice(
                        entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
                .sorted(Comparator.comparingLong(TraceContextSlice::totalNanos).reversed())
                .toList());

        long windowNanos = (toMicros - fromMicros) * NANOS_PER_MICRO;
        long accounted = slices.stream().mapToLong(TraceContextSlice::totalNanos).sum();
        slices.add(new TraceContextSlice(
                TraceContextSlice.OWN_WORK, Math.max(0, windowNanos - accounted), 0));
        return List.copyOf(slices);
    }

    @Override
    public List<SpanInterval> spanIntervals(long traceId, long spanId, boolean selfOnly) {
        List<TraceSpanRecord> spans = traceRepository.spansOf(traceId);
        TraceSpanRecord target = spans.stream()
                .filter(span -> span.spanId() == spanId)
                .findFirst()
                .orElse(null);
        if (target == null) {
            return List.of();
        }

        if (!selfOnly) {
            return List.of(intervalOf(target));
        }
        return selfIntervals(target, childrenOf(spans, spanId));
    }

    @Override
    public List<SpanInterval> operationIntervals(TraceOperationId operation) {
        return traceRepository.operationIntervals(operation);
    }

    @Override
    public TraceOperationsPage operations(TraceOperationListQuery query) {
        TraceOperationPage page = traceRepository.operations(query);
        return new TraceOperationsPage(
                page.operations().stream()
                        .map(operation -> new TraceOperationRow(
                                operation.name(),
                                operation.kind(),
                                operation.eventType(),
                                operation.count(),
                                operation.errorCount(),
                                operation.spanCount(),
                                operation.totalNanos(),
                                operation.p50Nanos(),
                                operation.p95Nanos(),
                                operation.p99Nanos(),
                                operation.maxNanos()))
                        .toList(),
                page.totalMatching());
    }

    /**
     * Finds one operation's aggregate row by narrowing the list to its name and then matching the
     * whole identity, since a name alone is not one: an inbound and an outbound call can share it.
     * <p>
     * Built on the list read rather than on a query of its own, because the aggregation behind it is
     * not trivial and having two of it is how the list and the detail come to disagree. The name
     * filter does the narrowing in SQL; the exact match happens here.
     */
    @Override
    public Optional<TraceOperationRow> operation(TraceOperationId operation) {
        TraceOperationListQuery query = new TraceOperationListQuery(
                operation.name(),
                false,
                TraceOperationSortField.TOTAL_TIME,
                true,
                OPERATION_LOOKUP_LIMIT,
                0);

        return operations(query).operations().stream()
                .filter(row -> row.name().equals(operation.name())
                        && row.kind().equals(operation.kind())
                        && row.eventType().equals(operation.eventType()))
                .findFirst();
    }

    @Override
    public TraceOperationSummary operationSummary(TraceOperationId operation, int spanLimit) {
        List<TraceOperationSpanRow> spans = traceRepository.spanBreakdownOfOperation(operation, spanLimit).stream()
                .map(span -> new TraceOperationSpanRow(
                        span.name(),
                        span.occurrences(),
                        span.traceCount(),
                        span.totalNanos(),
                        span.selfNanos(),
                        span.p50Nanos(),
                        span.p50SelfNanos(),
                        span.maxNanos()))
                .toList();

        TraceOperationThreadsRecord threads = traceRepository.threadsOfOperation(operation);
        return new TraceOperationSummary(
                spans,
                new TraceOperationThreads(
                        threads.distinctThreads(),
                        threads.platformSpans(),
                        threads.virtualSpans(),
                        threads.unknownSpans()));
    }

    @Override
    public TraceSpanEvents eventsInSpan(long traceId, long spanId) {
        return spanOf(traceId, spanId)
                .map(span -> {
                    ThreadWindowEventsPage page = traceRepository.eventsInSpan(
                            span.threadHash(),
                            toMillis(span.startEpochMicros()), toMillis(endMicrosOf(span)));

                    List<TraceEventRow> events = page.events().stream()
                            .map(event -> new TraceEventRow(
                                    event.eventType(),
                                    event.startEpochMillis(),
                                    event.durationNanos(),
                                    event.fields()))
                            .toList();
                    return new TraceSpanEvents(events, page.truncated());
                })
                .orElse(TraceSpanEvents.EMPTY);
    }

    private Optional<TraceSpanRecord> spanOf(long traceId, long spanId) {
        return traceRepository.spansOf(traceId).stream()
                .filter(span -> span.spanId() == spanId)
                .findFirst();
    }

    /**
     * Orders the spans the way the waterfall reads them: each root followed by its subtree,
     * siblings by start time.
     * <p>
     * Two malformed shapes have to survive this without losing a span. A span whose parent is
     * missing — it fell below the event threshold, or was never instrumented — is promoted to a
     * root, and its dangling parent id is dropped so the tree the UI receives is self-consistent.
     * A parent cycle, which cannot arise from correct instrumentation, leaves its members
     * unreachable from any root; rather than dropping them, the traversal breaks into the earliest
     * one and carries on until every id has been placed.
     * <p>
     * Rows sharing an id are the one shape that does lose a span: a span id identifies a span, and
     * the derivation dedupes on it before the primary key enforces it. Only the first row of an id
     * is drawn — a defence kept even though a well-formed database cannot produce the shape, because
     * a trace must render whatever the database holds.
     */
    private static List<TraceSpanRow> assemble(List<TraceSpanRecord> spans) {
        Set<Long> known = new HashSet<>();
        for (TraceSpanRecord span : spans) {
            known.add(span.spanId());
        }

        Map<Long, List<TraceSpanRecord>> childrenByParent = new HashMap<>();
        List<TraceSpanRecord> roots = new ArrayList<>();
        for (TraceSpanRecord span : spans) {
            Long parentId = span.parentSpanId();
            if (parentId == null || !known.contains(parentId) || parentId == span.spanId()) {
                roots.add(span);
            } else {
                childrenByParent.computeIfAbsent(parentId, _ -> new ArrayList<>()).add(span);
            }
        }
        roots.sort(Comparator.comparingLong(TraceSpanRecord::startEpochMicros));
        childrenByParent.values()
                .forEach(children -> children.sort(Comparator.comparingLong(TraceSpanRecord::startEpochMicros)));

        Map<Long, Long> criticalMicros = criticalPathMicros(roots, childrenByParent);

        List<TraceSpanRow> ordered = new ArrayList<>(spans.size());
        Set<Long> visited = new HashSet<>();
        traverse(roots, childrenByParent, criticalMicros, visited, ordered);

        // Whatever is left belongs to a parent cycle. Breaking in at each still-unplaced span, in
        // start order, renders a malformed trace as a flatter tree instead of an empty one. The
        // pass is driven by the spans themselves rather than by a count of what has been placed:
        // ids the derivation should have made unique may not be, and a trace must not fail to
        // render over it.
        List<TraceSpanRecord> byStart = new ArrayList<>(spans);
        byStart.sort(Comparator.comparingLong(TraceSpanRecord::startEpochMicros));
        for (TraceSpanRecord span : byStart) {
            if (!visited.contains(span.spanId())) {
                traverse(List.of(span), childrenByParent, criticalMicros, visited, ordered);
            }
        }
        return ordered;
    }

    /**
     * Walks each start span and its subtree depth-first, appending rows in draw order. Start spans
     * are rendered without a parent: they head the tree the caller sees, whatever their recorded
     * {@code parentSpanId} said.
     */
    private static void traverse(
            List<TraceSpanRecord> startSpans,
            Map<Long, List<TraceSpanRecord>> childrenByParent,
            Map<Long, Long> criticalMicros,
            Set<Long> visited,
            List<TraceSpanRow> ordered) {

        Deque<Placement> pending = new ArrayDeque<>();
        for (int i = startSpans.size() - 1; i >= 0; i--) {
            pending.push(new Placement(startSpans.get(i), 0, null));
        }
        while (!pending.isEmpty()) {
            Placement placement = pending.pop();
            TraceSpanRecord span = placement.span();
            if (!visited.add(span.spanId())) {
                continue;
            }
            List<TraceSpanRecord> children = childrenByParent.getOrDefault(span.spanId(), List.of());
            ordered.add(toRow(span, placement.depth(), placement.parentSpanId(),
                    criticalNanosOf(span, criticalMicros)));
            for (int i = children.size() - 1; i >= 0; i--) {
                pending.push(new Placement(children.get(i), placement.depth() + 1, span.spanId()));
            }
        }
    }

    /**
     * The span's own share of the trace's critical path, in nanoseconds.
     * <p>
     * Capped at the span's duration: the walk works in microseconds, and a span whose bounds round
     * outwards could otherwise be credited a sliver more than it ever ran for.
     */
    private static long criticalNanosOf(TraceSpanRecord span, Map<Long, Long> criticalMicros) {
        long micros = criticalMicros.getOrDefault(span.spanId(), 0L);
        return Math.min(micros * NANOS_PER_MICRO, span.durationNanos());
    }

    /**
     * How much of the trace's end-to-end duration each span is personally responsible for, in
     * microseconds keyed by span id. A span missing from the result contributed nothing: shortening
     * it would not have shortened the trace.
     * <p>
     * The walk runs backwards from the end of the trace. Within a span's window the last child to
     * finish is what held the span open, so that child owns the stretch it covers and the span owns
     * what is left between and around its children; recursing into each child in turn gives the
     * chain of work that actually determined the total. A child that ran entirely inside a
     * later-finishing sibling's window is therefore credited nothing at all — shortening it would
     * have changed no total, which is the whole point of drawing the path.
     * <p>
     * Children are taken to block their parent. That is true by construction on the parent's own
     * thread, and it is the intended reading of a parent that hands work off and waits for it. A
     * parent that forked a cross-thread child and never waited will see that child credited time it
     * did not really cost — telling the two apart needs the thread's state, not the span tree.
     * <p>
     * The roots are walked as the children of one window covering the whole trace, so a trace that
     * recorded more than one root — which correct instrumentation cannot produce, but a recording
     * that began mid-request can — still attributes every stretch of its own timeline. That outer
     * window owns nothing itself; the gaps between roots belong to no span.
     */
    private static Map<Long, Long> criticalPathMicros(
            List<TraceSpanRecord> roots, Map<Long, List<TraceSpanRecord>> childrenByParent) {

        Map<Long, Long> critical = new HashMap<>();
        if (roots.isEmpty()) {
            return critical;
        }

        long from = Long.MAX_VALUE;
        long to = Long.MIN_VALUE;
        for (TraceSpanRecord root : roots) {
            from = Math.min(from, root.startEpochMicros());
            to = Math.max(to, endMicrosOf(root));
        }

        Deque<Window> pending = new ArrayDeque<>();
        attribute(roots, from, to, null, critical, pending);

        // Iterative for the same reason the tree traversal is: a trace deep enough to overflow the
        // stack must still render. The visited set also stops a parent cycle from walking forever.
        Set<Long> visited = new HashSet<>();
        while (!pending.isEmpty()) {
            Window window = pending.pop();
            TraceSpanRecord span = window.span();
            if (!visited.add(span.spanId())) {
                continue;
            }
            attribute(childrenByParent.getOrDefault(span.spanId(), List.of()),
                    window.from(), window.to(), span, critical, pending);
        }
        return critical;
    }

    /**
     * Splits one window between the span that owns it and the children that held it open, crediting
     * the owner with what no child covered and queueing each covering child for its own turn.
     * <p>
     * Children are read in the order they <em>finished</em>, latest first, which is what the walk is
     * actually asking: at any instant, whatever finishes last from here is what the owner is still
     * waiting on. Start order will not do — a child that began earlier can finish later, and reading
     * by start would credit the shorter sibling and skip the one that really held the window open.
     * <p>
     * The cursor only ever moves left, so a child starting at or after it was already covered by a
     * later-finishing sibling and is skipped entirely, as is one that ended before the window began.
     * A {@code null} owner is the outer trace window, which credits nobody.
     */
    private static void attribute(
            List<TraceSpanRecord> children,
            long from,
            long to,
            TraceSpanRecord owner,
            Map<Long, Long> critical,
            Deque<Window> pending) {

        // A copy: the caller's list is in start order, which the tree traversal draws from.
        List<TraceSpanRecord> byEnd = new ArrayList<>(children);
        byEnd.sort(Comparator.comparingLong(TraceManagerImpl::endMicrosOf).reversed());

        long cursor = to;
        for (TraceSpanRecord child : byEnd) {
            long childStart = child.startEpochMicros();
            long childEnd = endMicrosOf(child);
            if (childStart >= cursor || childEnd <= from) {
                continue;
            }
            long clampedStart = Math.max(childStart, from);
            long clampedEnd = Math.min(childEnd, cursor);
            if (owner != null && clampedEnd < cursor) {
                critical.merge(owner.spanId(), cursor - clampedEnd, Long::sum);
            }
            pending.push(new Window(child, clampedStart, clampedEnd));
            cursor = clampedStart;
        }
        if (owner != null && cursor > from) {
            critical.merge(owner.spanId(), cursor - from, Long::sum);
        }
    }

    /**
     * The span's window with its children's windows cut out, so a flamegraph scoped to it shows only
     * the samples taken while the span was doing its own work.
     * <p>
     * The cut is half-open on both sides: a child occupies {@code [childFrom, childTo]} and the
     * sample filter matches inclusively, so the surrounding segments have to stop one millisecond
     * short of it. Cutting at the child's own bounds would leave a sample landing on the child's
     * first or last millisecond counted in the child <em>and</em> in the parent's self time.
     * <p>
     * Only same-thread children are cut out, for the reason given in
     * the stored self time: a child on another thread never occupied this thread's window, so
     * punching a hole for it would drop the parent's own samples.
     */
    private static List<SpanInterval> selfIntervals(TraceSpanRecord span, List<TraceSpanRecord> children) {
        long from = toMillis(span.startEpochMicros());
        long to = toMillis(endMicrosOf(span));

        List<SpanInterval> intervals = new ArrayList<>();
        long cursor = from;
        for (long[] window : clippedChildWindows(span, children)) {
            long childFrom = toMillis(window[0]);
            long childTo = toMillis(window[1]);
            if (childFrom > cursor) {
                intervals.add(new SpanInterval(span.threadHash(), cursor, childFrom - 1));
            }
            cursor = Math.max(cursor, childTo + 1);
        }
        if (cursor <= to) {
            intervals.add(new SpanInterval(span.threadHash(), cursor, to));
        }
        return intervals;
    }

    /**
     * The children that shared the span's thread, which are the only ones whose windows overlap the
     * parent's in a way that hides the parent's own work.
     */
    private static List<TraceSpanRecord> sameThreadAs(TraceSpanRecord span, List<TraceSpanRecord> children) {
        return children.stream()
                .filter(child -> child.threadHash() == span.threadHash())
                .toList();
    }

    /**
     * The stretches of the span's window that its children occupied: same-thread children clipped to
     * the parent's own bounds and reduced to non-overlapping {@code [from, to]} microsecond windows,
     * ordered by start. What is left over between them is the parent's own work.
     * <p>
     * The same reduction the derivation runs in SQL to store {@code self_duration}, kept here
     * because {@link #selfIntervals} needs the windows themselves rather than their total: a
     * flamegraph has to know <em>which</em> stretches to exclude, not how many nanoseconds they came
     * to. The two must stay in step — same same-thread rule, same clipping.
     */
    private static List<long[]> clippedChildWindows(
            TraceSpanRecord span, List<TraceSpanRecord> children) {

        long from = span.startEpochMicros();
        long to = endMicrosOf(span);

        List<long[]> windows = sameThreadAs(span, children).stream()
                .map(child -> new long[]{
                        clamp(child.startEpochMicros(), from, to),
                        clamp(endMicrosOf(child), from, to)})
                .sorted(Comparator.comparingLong(window -> window[0]))
                .collect(Collectors.toCollection(ArrayList::new));

        List<long[]> merged = new ArrayList<>();
        for (long[] window : windows) {
            if (!merged.isEmpty() && window[0] <= merged.getLast()[1]) {
                merged.getLast()[1] = Math.max(merged.getLast()[1], window[1]);
            } else {
                merged.add(window);
            }
        }
        return merged;
    }

    private static long clamp(long value, long min, long max) {
        return Math.min(Math.max(value, min), max);
    }

    private static List<TraceSpanRecord> childrenOf(List<TraceSpanRecord> spans, long spanId) {
        return spans.stream()
                .filter(span -> span.parentSpanId() != null && span.parentSpanId() == spanId)
                .toList();
    }

    private static SpanInterval intervalOf(TraceSpanRecord span) {
        return new SpanInterval(
                span.threadHash(), toMillis(span.startEpochMicros()), toMillis(endMicrosOf(span)));
    }

    private static long endMicrosOf(TraceSpanRecord span) {
        return span.startEpochMicros() + span.durationNanos() / NANOS_PER_MICRO;
    }

    /**
     * Crosses into the millisecond domain the events table is keyed on. Flooring rather than rounding
     * is what makes the bound land on the millisecond a sample taken at that instant was filed under.
     */
    private static long toMillis(long micros) {
        return Math.floorDiv(micros, MICROS_PER_MILLI);
    }

    private static TraceRow toRow(TraceSummaryRecord trace) {
        return new TraceRow(
                toHex(trace.traceId()),
                trace.rootName(),
                trace.rootKind(),
                trace.rootEventType(),
                trace.startMillisFromBeginning(),
                trace.startEpochMillis(),
                trace.durationNanos(),
                trace.spanCount(),
                trace.errorCount(),
                trace.hasPlatformSpan());
    }

    private static TraceSpanRow toRow(
            TraceSpanRecord span, int depth, Long parentSpanId, long criticalPathNanos) {

        return new TraceSpanRow(
                toHex(span.spanId()),
                parentSpanId == null ? null : toHex(parentSpanId),
                span.name(),
                span.kind(),
                span.status(),
                span.errorType(),
                span.startMillisFromBeginning(),
                span.startEpochMicros(),
                span.durationNanos(),
                span.selfDurationNanos(),
                criticalPathNanos,
                depth,
                Long.toString(span.threadHash()),
                span.threadName(),
                span.isVirtual(),
                span.eventType(),
                span.attributes(),
                span.eventFields());
    }

    /**
     * Ids cross the wire as 16-char hex: a 64-bit value exceeds JavaScript's safe integer range, and
     * hex is also how every other tracer renders them.
     */
    private static String toHex(long id) {
        return String.format("%016x", id);
    }

    /** A span queued for emission, with the position the tree gives it. */
    private record Placement(TraceSpanRecord span, int depth, Long parentSpanId) {
    }

    /**
     * A span queued for critical-path attribution, with the stretch of its parent's window it was
     * found to be holding open — its own bounds clipped to what the parent had left to give.
     */
    private record Window(TraceSpanRecord span, long from, long to) {
    }
}
