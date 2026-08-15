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
import cafe.jeffrey.profile.manager.model.trace.TraceDetail;
import cafe.jeffrey.profile.manager.model.trace.TraceEventRow;
import cafe.jeffrey.profile.manager.model.trace.TraceOperationRow;
import cafe.jeffrey.profile.manager.model.trace.TraceOperationSpanRow;
import cafe.jeffrey.profile.manager.model.trace.TraceOperationSummary;
import cafe.jeffrey.profile.manager.model.trace.TraceOperationThreads;
import cafe.jeffrey.profile.manager.model.trace.TraceOverview;
import cafe.jeffrey.profile.manager.model.trace.TraceRow;
import cafe.jeffrey.profile.manager.model.trace.TraceSpanEvents;
import cafe.jeffrey.profile.manager.model.trace.TraceSpanRow;
import cafe.jeffrey.provider.profile.api.EventFieldRecord;
import cafe.jeffrey.provider.profile.api.ThreadWindowEventsPage;
import cafe.jeffrey.provider.profile.api.TraceOperationId;
import cafe.jeffrey.provider.profile.api.TraceOperationThreadsRecord;
import cafe.jeffrey.provider.profile.api.TraceOverviewRecord;
import cafe.jeffrey.provider.profile.api.TraceRepository;
import cafe.jeffrey.provider.profile.api.TraceSpanRecord;
import cafe.jeffrey.provider.profile.api.TraceSummaryRecord;
import cafe.jeffrey.shared.common.model.SpanInterval;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
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

    private final TraceRepository traceRepository;

    public TraceManagerImpl(TraceRepository traceRepository) {
        this.traceRepository = traceRepository;
    }

    @Override
    public List<TraceRow> slowestTraces(int limit) {
        return traceRepository.slowestTraces(limit).stream()
                .map(TraceManagerImpl::toRow)
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
    public List<TraceOperationRow> operations(int limit) {
        return traceRepository.operations(limit).stream()
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
                        operation.maxNanos()))
                .toList();
    }

    @Override
    public TraceOperationSummary operationSummary(TraceOperationId operation, int spanLimit) {
        List<TraceOperationSpanRow> spans = traceRepository.spanBreakdownOfOperation(operation, spanLimit).stream()
                .map(span -> new TraceOperationSpanRow(
                        span.name(),
                        span.occurrences(),
                        span.traceCount(),
                        span.totalNanos(),
                        span.p50Nanos(),
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

        List<TraceSpanRow> ordered = new ArrayList<>(spans.size());
        Set<Long> visited = new HashSet<>();
        traverse(roots, childrenByParent, visited, ordered);

        // Whatever is left belongs to a parent cycle. Breaking in at each still-unplaced span, in
        // start order, renders a malformed trace as a flatter tree instead of an empty one. The
        // pass is driven by the spans themselves rather than by a count of what has been placed:
        // ids the derivation should have made unique may not be, and a trace must not fail to
        // render over it.
        List<TraceSpanRecord> byStart = new ArrayList<>(spans);
        byStart.sort(Comparator.comparingLong(TraceSpanRecord::startEpochMicros));
        for (TraceSpanRecord span : byStart) {
            if (!visited.contains(span.spanId())) {
                traverse(List.of(span), childrenByParent, visited, ordered);
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
                    selfDurationOf(span, children)));
            for (int i = children.size() - 1; i >= 0; i--) {
                pending.push(new Placement(children.get(i), placement.depth() + 1, span.spanId()));
            }
        }
    }

    /**
     * The span's own time: its duration minus what its children accounted for. Only children that
     * ran on the span's own thread are subtracted — work handed to another thread runs beside the
     * parent rather than instead of it — and overlapping children are merged first so concurrent
     * work is not subtracted twice.
     * <p>
     * Children are clipped to the parent's own window, so a child that was recorded as outliving its
     * parent costs it only the stretch the two actually shared. The result is still floored at zero,
     * because microsecond rounding can make merged children marginally longer than the parent.
     */
    private static long selfDurationOf(TraceSpanRecord span, List<TraceSpanRecord> children) {
        long covered = 0;
        for (long[] window : clippedChildWindows(span, children)) {
            covered += window[1] - window[0];
        }
        return Math.max(0, span.durationNanos() - covered * NANOS_PER_MICRO);
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
     * {@link #selfDurationOf(TraceSpanRecord, List)}: a child on another thread never occupied this
     * thread's window, so punching a hole for it would drop the parent's own samples.
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
     * ordered by start. What is left over between them is the parent's own work — which is what both
     * {@link #selfDurationOf} and {@link #selfIntervals} are asking for, in their own units.
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
            TraceSpanRecord span, int depth, Long parentSpanId, long selfDurationNanos) {

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
                selfDurationNanos,
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
}
