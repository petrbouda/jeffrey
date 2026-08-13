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

import cafe.jeffrey.profile.manager.model.trace.TraceDetail;
import cafe.jeffrey.profile.manager.model.trace.TraceEventRow;
import cafe.jeffrey.profile.manager.model.trace.TraceOperationRow;
import cafe.jeffrey.profile.manager.model.trace.TraceRow;
import cafe.jeffrey.profile.manager.model.trace.TraceSpanRow;
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
import java.util.stream.Collectors;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Turns the flat span rows the repository returns into the tree the waterfall draws.
 * <p>
 * The assembly lives here rather than in SQL or in the browser because the same interval arithmetic
 * feeds the span-scoped flamegraph, which has to run server-side anyway.
 */
public class TraceManagerImpl implements TraceManager {

    private static final long NANOS_PER_MILLI = 1_000_000L;

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
    public Optional<TraceDetail> trace(long traceId) {
        List<TraceSpanRecord> spans = traceRepository.spansOf(traceId);
        if (spans.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new TraceDetail(summaryOf(traceId, spans), assemble(spans)));
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
    public List<TraceOperationRow> operations(int limit) {
        return traceRepository.operations(limit).stream()
                .map(operation -> new TraceOperationRow(
                        operation.name(),
                        operation.kind(),
                        operation.count(),
                        operation.errorCount(),
                        operation.totalNanos(),
                        operation.p50Nanos(),
                        operation.p95Nanos(),
                        operation.maxNanos()))
                .toList();
    }

    @Override
    public List<TraceEventRow> eventsInSpan(long traceId, long spanId) {
        return spanOf(traceId, spanId)
                .map(span -> traceRepository
                        .eventsInSpan(span.threadHash(), span.startEpochMillis(), endMillisOf(span))
                        .stream()
                        .map(event -> new TraceEventRow(
                                event.eventType(),
                                event.startEpochMillis(),
                                event.durationNanos(),
                                event.fields()))
                        .toList())
                .orElseGet(List::of);
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
     * one and carries on until every span has been placed.
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
        roots.sort(Comparator.comparingLong(TraceSpanRecord::startEpochMillis));
        childrenByParent.values()
                .forEach(children -> children.sort(Comparator.comparingLong(TraceSpanRecord::startEpochMillis)));

        List<TraceSpanRow> ordered = new ArrayList<>(spans.size());
        Set<Long> visited = new HashSet<>();
        traverse(roots, childrenByParent, visited, ordered);

        // Whatever is left belongs to a parent cycle. Break in at the earliest unplaced span and
        // keep going, so a malformed trace renders as a flatter tree instead of an empty one.
        while (visited.size() < spans.size()) {
            TraceSpanRecord unplaced = spans.stream()
                    .filter(span -> !visited.contains(span.spanId()))
                    .min(Comparator.comparingLong(TraceSpanRecord::startEpochMillis))
                    .orElseThrow();
            traverse(List.of(unplaced), childrenByParent, visited, ordered);
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
     * The span's own time: its duration minus what its children accounted for. Overlapping children
     * are merged first so concurrent work is not subtracted twice, and the result is floored at zero
     * because a parent that hands off to another thread can legitimately be shorter than its
     * children.
     */
    private static long selfDurationOf(TraceSpanRecord span, List<TraceSpanRecord> children) {
        long covered = 0;
        for (long[] window : mergedWindows(children)) {
            covered += window[1] - window[0];
        }
        return Math.max(0, span.durationNanos() - covered * NANOS_PER_MILLI);
    }

    /**
     * The span's window with its children's windows cut out, so a flamegraph scoped to it shows only
     * the samples taken while the span was doing its own work.
     */
    private static List<SpanInterval> selfIntervals(TraceSpanRecord span, List<TraceSpanRecord> children) {
        long from = span.startEpochMillis();
        long to = endMillisOf(span);

        List<SpanInterval> intervals = new ArrayList<>();
        long cursor = from;
        for (long[] window : mergedWindows(children)) {
            long childFrom = Math.max(window[0], from);
            long childTo = Math.min(window[1], to);
            if (childFrom > cursor) {
                intervals.add(new SpanInterval(span.threadHash(), cursor, childFrom));
            }
            cursor = Math.max(cursor, childTo);
        }
        if (cursor < to) {
            intervals.add(new SpanInterval(span.threadHash(), cursor, to));
        }
        return intervals;
    }

    /**
     * Children reduced to non-overlapping {@code [from, to]} millisecond windows, ordered by start.
     */
    private static List<long[]> mergedWindows(List<TraceSpanRecord> children) {
        List<long[]> windows = children.stream()
                .map(child -> new long[]{child.startEpochMillis(), endMillisOf(child)})
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

    private static List<TraceSpanRecord> childrenOf(List<TraceSpanRecord> spans, long spanId) {
        return spans.stream()
                .filter(span -> span.parentSpanId() != null && span.parentSpanId() == spanId)
                .toList();
    }

    private static SpanInterval intervalOf(TraceSpanRecord span) {
        return new SpanInterval(span.threadHash(), span.startEpochMillis(), endMillisOf(span));
    }

    private static long endMillisOf(TraceSpanRecord span) {
        return span.startEpochMillis() + span.durationNanos() / NANOS_PER_MILLI;
    }

    /**
     * Rebuilds the trace's summary from the spans already in hand, rather than reading the traces
     * table a second time for a row the caller is about to render anyway.
     */
    private static TraceRow summaryOf(long traceId, List<TraceSpanRecord> spans) {
        TraceSpanRecord root = spans.stream()
                .min(Comparator
                        .comparing((TraceSpanRecord span) -> span.parentSpanId() != null)
                        .thenComparingLong(TraceSpanRecord::startEpochMillis))
                .orElseThrow();

        long start = spans.stream().mapToLong(TraceSpanRecord::startEpochMillis).min().orElse(0);
        long end = spans.stream().mapToLong(TraceManagerImpl::endMillisOf).max().orElse(start);
        long errors = spans.stream().filter(span -> "ERROR".equals(span.status())).count();

        return new TraceRow(
                toHex(traceId),
                root.name(),
                root.kind(),
                spans.stream().mapToLong(TraceSpanRecord::startMillisFromBeginning).min().orElse(0),
                start,
                (end - start) * NANOS_PER_MILLI,
                spans.size(),
                (int) errors);
    }

    private static TraceRow toRow(TraceSummaryRecord trace) {
        return new TraceRow(
                toHex(trace.traceId()),
                trace.rootName(),
                trace.rootKind(),
                trace.startMillisFromBeginning(),
                trace.startEpochMillis(),
                trace.durationNanos(),
                trace.spanCount(),
                trace.errorCount());
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
                span.attributes(),
                span.startMillisFromBeginning(),
                span.startEpochMillis(),
                span.durationNanos(),
                selfDurationNanos,
                depth,
                Long.toString(span.threadHash()),
                span.threadName(),
                span.eventType());
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
