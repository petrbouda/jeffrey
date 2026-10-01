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

package cafe.jeffrey.profile.manager;

import cafe.jeffrey.profile.manager.model.trace.EventFieldRow;
import cafe.jeffrey.profile.manager.model.trace.TraceContext;
import cafe.jeffrey.profile.manager.model.trace.TraceSpanRunRow;
import cafe.jeffrey.profile.manager.model.trace.TraceSpanRunMembers;
import cafe.jeffrey.profile.manager.model.trace.TraceRunDurations;
import cafe.jeffrey.profile.manager.model.trace.TracePromotedGroup;
import cafe.jeffrey.profile.manager.model.trace.TraceExportSource;
import cafe.jeffrey.profile.manager.model.trace.TraceContextSlice;
import cafe.jeffrey.profile.manager.model.trace.TraceDetail;
import cafe.jeffrey.profile.manager.model.trace.TraceExceptionRow;
import cafe.jeffrey.profile.manager.model.trace.TraceNotificationRow;
import cafe.jeffrey.profile.manager.model.trace.TracePause;
import cafe.jeffrey.profile.manager.model.trace.TraceThrottleWindow;
import cafe.jeffrey.profile.manager.model.trace.TraceEventRow;
import cafe.jeffrey.profile.manager.model.trace.TraceNotificationGroupRow;
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
import cafe.jeffrey.profile.manager.model.trace.TraceStacktrace;
import cafe.jeffrey.profile.manager.model.trace.TraceStackFrameRow;
import cafe.jeffrey.provider.profile.api.EventFieldRecord;
import cafe.jeffrey.provider.profile.api.ThreadWindowEventsPage;
import cafe.jeffrey.provider.profile.api.TraceOperationId;
import cafe.jeffrey.provider.profile.api.TraceNotificationListQuery;
import cafe.jeffrey.provider.profile.api.TraceOperationListQuery;
import cafe.jeffrey.provider.profile.api.TraceOperationPage;
import cafe.jeffrey.provider.profile.api.TraceOperationSortField;
import cafe.jeffrey.provider.profile.api.TraceOperationThreadsRecord;
import cafe.jeffrey.provider.profile.api.TraceOverviewRecord;
import cafe.jeffrey.provider.profile.api.TraceContextCategory;
import cafe.jeffrey.provider.profile.api.TraceRepository;
import cafe.jeffrey.provider.profile.api.TraceSpanContextRecord;
import cafe.jeffrey.provider.profile.api.TraceExceptionRecord;
import cafe.jeffrey.provider.profile.api.TraceNotificationRecord;
import cafe.jeffrey.provider.profile.api.TraceSpanRecord;
import cafe.jeffrey.provider.profile.api.TracePromotedGroupRecord;
import cafe.jeffrey.provider.profile.api.TraceWindowRecord;
import cafe.jeffrey.provider.profile.api.TraceSpanRunRecord;
import cafe.jeffrey.provider.profile.api.TraceSpanShape;
import cafe.jeffrey.provider.profile.api.TraceSummaryRecord;
import cafe.jeffrey.provider.profile.api.TraceTimelineBucketRecord;
import cafe.jeffrey.provider.profile.api.EventFrame;
import cafe.jeffrey.microscope.model.SpanInterval;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    /**
     * How many identical sibling leaves it takes to send them as one summed row. Nobody reads a
     * hundred identical rows one by one, and below it a run is cheap enough on the wire that the
     * waterfall still gets every member and folds it itself, ticks and all.
     */
    static final int RUN_FOLD_THRESHOLD = 100;
    private static final int RUN_HISTOGRAM_BUCKETS = 12;
    private final TraceRepository traceRepository;

    public TraceManagerImpl(TraceRepository traceRepository) {
        this.traceRepository = traceRepository;
    }

    @Override
    public List<TraceTimelineBucket> timelineOfOperation(TraceOperationId operation, int buckets) {
        return toBuckets(traceRepository.timelineOfOperation(operation, buckets));
    }

    private static List<TraceTimelineBucket> toBuckets(List<TraceTimelineBucketRecord> records) {
        return records.stream()
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
    public List<TraceRow> slowestTracesOfOperation(TraceOperationId operation, int limit) {
        return traceRepository.slowestTracesOfOperation(operation, limit).stream()
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
                overview.notificationCount(),
                overview.urgentNotificationCount(),
                overview.avgNanos(),
                overview.p95Nanos(),
                overview.p99Nanos(),
                overview.maxNanos(),
                overview.totalNanos(),
                overview.distinctOperations());
    }

    @Override
    public Optional<TraceDetail> trace(long traceId) {
        // The stored header rather than one rebuilt from the spans: the derivation already settled
        // the root and the duration in nanoseconds, and recomputing them here from microsecond-
        // truncated span bounds made the same trace report one duration in the list and a shorter
        // one in its own detail.
        return traceRepository.summaryOf(traceId)
                .flatMap(summary -> detailOf(traceId, summary));
    }

    private Optional<TraceDetail> detailOf(long traceId, TraceSummaryRecord summary) {
        List<TraceSpanShape> shapes = traceRepository.shapesOf(traceId, RUN_FOLD_THRESHOLD);
        if (shapes.isEmpty()) {
            return Optional.empty();
        }
        TraceSkeleton skeleton = TraceSkeleton.of(shapes);

        Map<Long, TraceSpanRecord> drawn = new HashMap<>();
        for (TraceSpanRecord span : traceRepository.spansOf(traceId, RUN_FOLD_THRESHOLD)) {
            drawn.putIfAbsent(span.spanId(), span);
        }
        Map<Long, TraceSpanRunRecord> runs = new HashMap<>();
        for (TraceSpanRunRecord run : traceRepository.runsOf(traceId, RUN_FOLD_THRESHOLD)) {
            runs.put(run.runId(), run);
        }

        List<TraceNotificationRecord> notifications = traceRepository.notificationsOf(traceId);
        List<TraceExceptionRecord> exceptions = traceRepository.exceptionsOf(traceId);
        Map<Long, List<String>> entriesByRun = entriesByRun(skeleton, notifications, exceptions);

        List<TraceSpanRow> spanRows = new ArrayList<>();
        List<TraceSpanRunRow> runRows = new ArrayList<>();
        for (TraceSkeleton.Slot slot : skeleton.ordered()) {
            switch (slot) {
                case TraceSkeleton.SpanSlot spanSlot -> {
                    TraceSpanRecord span = drawn.get(spanSlot.spanId());
                    if (span != null) {
                        spanRows.add(toRow(span, spanSlot.depth(), spanSlot.parentSpanId(),
                                skeleton.criticalNanosOf(span.spanId(), span.durationNanos())));
                    }
                }
                case TraceSkeleton.RunSlot runSlot -> {
                    TraceSpanRunRecord run = runs.get(runSlot.runId());
                    if (run != null) {
                        runRows.add(toRunRow(run, runSlot, spanRows.size(), skeleton,
                                entriesByRun.getOrDefault(run.runId(), List.of())));
                    }
                }
            }
        }

        return Optional.of(new TraceDetail(
                toRow(summary),
                skeleton.window(),
                spanRows,
                runRows,
                skeleton.threadCount(),
                notifications.stream().map(TraceManagerImpl::toRow).toList(),
                exceptions.stream().map(TraceManagerImpl::toRow).toList(),
                eventFieldsOf(spanRows, runRows)));
    }

    /**
     * The members a notification or an exception points at, per run — what lets the waterfall
     * resolve an entry on its rail to the folded row that hides its span, without paging the run.
     */
    private static Map<Long, List<String>> entriesByRun(
            TraceSkeleton skeleton,
            List<TraceNotificationRecord> notifications,
            List<TraceExceptionRecord> exceptions) {

        Set<Long> entrySpanIds = new LinkedHashSet<>();
        notifications.stream()
                .map(TraceNotificationRecord::spanId)
                .filter(Objects::nonNull)
                .forEach(entrySpanIds::add);
        exceptions.stream()
                .map(TraceExceptionRecord::spanId)
                .forEach(entrySpanIds::add);

        Map<Long, List<String>> byRun = new HashMap<>();
        for (long spanId : entrySpanIds) {
            skeleton.shapeOf(spanId)
                    .map(TraceSpanShape::runId)
                    .ifPresent(runId -> byRun.computeIfAbsent(runId, _ -> new ArrayList<>()).add(toHex(spanId)));
        }
        return byRun;
    }

    private static TraceSpanRunRow toRunRow(
            TraceSpanRunRecord run,
            TraceSkeleton.RunSlot slot,
            int position,
            TraceSkeleton skeleton,
            List<String> entrySpanIds) {

        List<TraceSpanShape> members = skeleton.membersOf(run.runId());
        long[] durations = new long[members.size()];
        long critical = 0;
        long lastEnd = Long.MIN_VALUE;
        Set<Long> threads = new HashSet<>();
        for (int i = 0; i < durations.length; i++) {
            TraceSpanShape member = members.get(i);
            durations[i] = member.durationNanos();
            critical += skeleton.criticalNanosOf(member.spanId(), member.durationNanos());
            lastEnd = Math.max(lastEnd, TraceSkeleton.endMicrosOf(member));
            threads.add(member.threadHash());
        }
        Arrays.sort(durations);
        TraceSpanShape first = members.getFirst();

        return new TraceSpanRunRow(
                toHex(run.runId()),
                slot.parentSpanId() == null ? null : toHex(slot.parentSpanId()),
                position,
                slot.depth(),
                run.name(),
                run.kind(),
                run.eventType(),
                run.ioOrigin(),
                run.synthesized(),
                toHex(first.threadHash()),
                run.firstThreadName(),
                threads.size(),
                TraceRunDurations.of(durations, RUN_HISTOGRAM_BUCKETS),
                critical,
                first.startEpochMicros(),
                lastEnd,
                TraceRunCoverage.of(members, skeleton.window()),
                entrySpanIds);
    }

    /**
     * The field metadata for the event types this trace's spans and runs came from, grouped by type.
     * <p>
     * Looked up for the types actually present rather than for every traced type, so a trace of one
     * HTTP request does not carry the schema of six JDBC events the UI will never draw. A folded
     * run's type is included: its members open the same span panel when they are loaded.
     */
    private Map<String, List<EventFieldRow>> eventFieldsOf(
            List<TraceSpanRow> spans, List<TraceSpanRunRow> runs) {

        List<String> eventTypes = Stream.concat(
                        spans.stream().map(TraceSpanRow::eventType),
                        runs.stream().map(TraceSpanRunRow::eventType))
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
     * The members are read a page at a time, but each is placed against the whole trace: its depth
     * and parent are its run's, and its critical-path share comes from the walk over every span —
     * the same figure it would carry were the run never folded.
     */
    @Override
    public Optional<TraceSpanRunMembers> runMembers(long traceId, long runId, int offset, int limit) {
        List<TraceSpanShape> shapes = traceRepository.shapesOf(traceId, RUN_FOLD_THRESHOLD);
        if (shapes.isEmpty()) {
            return Optional.empty();
        }
        TraceSkeleton skeleton = TraceSkeleton.of(shapes);
        Optional<TraceSkeleton.RunSlot> found = skeleton.runSlotOf(runId);
        if (found.isEmpty()) {
            return Optional.empty();
        }

        TraceSkeleton.RunSlot slot = found.get();
        long total = skeleton.membersOf(runId).size();
        List<TraceSpanRow> members = traceRepository
                .runMembers(traceId, runId, RUN_FOLD_THRESHOLD, offset, limit).stream()
                .map(member -> toRow(member, slot.depth(), slot.parentSpanId(),
                        skeleton.criticalNanosOf(member.spanId(), member.durationNanos())))
                .toList();
        boolean hasMore = (long) offset + members.size() < total;
        return Optional.of(new TraceSpanRunMembers(members, total, hasMore));
    }

    @Override
    public Optional<TraceSpanRow> span(long traceId, long spanId) {
        return traceRepository.spanOf(traceId, spanId)
                .flatMap(span -> {
                    TraceSkeleton skeleton = TraceSkeleton.of(
                            traceRepository.shapesOf(traceId, RUN_FOLD_THRESHOLD));
                    return skeleton.slotOf(spanId)
                            .map(slot -> toRow(span, slot.depth(), slot.parentSpanId(),
                                    skeleton.criticalNanosOf(spanId, span.durationNanos())));
                });
    }

    @Override
    public Optional<TraceExportSource> export(long traceId) {
        return trace(traceId)
                .map(detail -> new TraceExportSource(
                        detail,
                        context(traceId),
                        traceRepository.promotedGroupsOf(traceId).stream()
                                .map(group -> new TracePromotedGroup(
                                        group.eventType(),
                                        group.eventFields(),
                                        group.count(),
                                        group.totalNanos(),
                                        group.maxNanos()))
                                .toList()));
    }

    /**
     * What the JVM was doing to this trace: the pauses that crossed it, what each span waited on,
     * and the ranked summary of where its time went.
     * <p>
     * The trace's window is the one its header stores — first span start to last span end, folded
     * members included — so a pause is looked for over exactly the stretch the waterfall draws: a
     * child that outlived its parent widens both, and the band would otherwise stop short of the
     * bar it explains.
     */
    @Override
    public TraceContext context(long traceId) {
        Optional<TraceWindowRecord> traceWindow = traceRepository.windowOf(traceId);
        if (traceWindow.isEmpty()) {
            return TraceContext.EMPTY;
        }

        long from = traceWindow.get().fromEpochMicros();
        long to = traceWindow.get().toEpochMicros();

        List<TracePause> pauses = traceRepository.pausesInWindow(from, to).stream()
                .map(pause -> new TracePause(
                        pause.category().name(),
                        pause.label(),
                        pause.fromEpochMicros(),
                        pause.durationNanos(),
                        pause.nested()))
                .toList();

        /*
         * Deliberately not folded into `pauses`, and deliberately not passed to summarise(). A
         * throttle window is an approximation of when, so it can be drawn beside the pauses but not
         * added to them: the summary's percentages are taken against the trace's own window, and a
         * window-derived total joining that sum would push the accounted time past what elapsed and
         * silently shrink "own work" to cover it.
         */
        List<TraceThrottleWindow> throttleWindows = traceRepository.throttledWindowsIn(from, to).stream()
                .map(window -> new TraceThrottleWindow(
                        window.fromEpochMicros(),
                        window.toEpochMicros(),
                        window.throttledNanos(),
                        window.throttledSlices(),
                        window.elapsedSlices(),
                        window.ratioPercent()))
                .toList();

        List<TraceSpanContextRecord> waits = traceRepository.spanContext(traceId);
        Map<String, List<TraceContextSlice>> spanWaits = waits.stream()
                .collect(Collectors.groupingBy(
                        wait -> toHex(wait.spanId()),
                        Collectors.collectingAndThen(
                                Collectors.toList(), TraceManagerImpl::toSlices)));

        return new TraceContext(
                pauses, throttleWindows, spanWaits,
                summarise(traceRepository.promotedGroupsOf(traceId), pauses, waits, from, to));
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
     * <p>
     * Within a category the clipped pauses are <em>merged</em> before being summed, for the same
     * reason the denominator is the window rather than the sum of the spans: a levelled GC phase
     * runs inside its collection pause, so adding the two adds the same stopped microsecond twice.
     * The count stays a count of events, because "how many times" and "for how long" are different
     * questions and only the second one double-counts.
     * <p>
     * The categories the derivation promotes into synthesized leaf spans — I/O, locks, parks and
     * the rest — no longer arrive as waits at all; their totals are rebuilt here from the
     * synthesized spans themselves, so the panel's numbers and the waterfall's bars are one source
     * of truth by construction. They arrive pre-totalled per event type and payload, because a trace
     * can hold a million of them and the sum is all this needs.
     */
    private static List<TraceContextSlice> summarise(
            List<TracePromotedGroupRecord> promoted,
            List<TracePause> pauses,
            List<TraceSpanContextRecord> waits,
            long fromMicros,
            long toMicros) {

        Map<String, List<long[]>> intervals = new LinkedHashMap<>();
        Map<String, long[]> totals = new LinkedHashMap<>();
        for (TracePause pause : pauses) {
            long from = Math.max(fromMicros, pause.startEpochMicros());
            long to = Math.min(toMicros, pause.startEpochMicros() + pause.durationNanos() / NANOS_PER_MICRO);
            totals.computeIfAbsent(pause.category(), _ -> new long[2])[1]++;
            if (to > from) {
                intervals.computeIfAbsent(pause.category(), _ -> new ArrayList<>())
                        .add(new long[] {from, to});
            }
        }
        intervals.forEach((category, categoryIntervals) ->
                totals.get(category)[0] = mergedMicros(categoryIntervals) * NANOS_PER_MICRO);
        for (TraceSpanContextRecord wait : waits) {
            long[] slot = totals.computeIfAbsent(wait.category().name(), _ -> new long[2]);
            slot[0] += wait.totalNanos();
            slot[1] += wait.occurrences();
        }
        for (TracePromotedGroupRecord group : promoted) {
            TraceContextCategory.fromEventType(group.eventType()).ifPresent(category -> {
                long[] slot = totals.computeIfAbsent(category.name(), _ -> new long[2]);
                slot[0] += group.totalNanos();
                slot[1] += group.count();
            });
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

    /**
     * How much time a set of half-open intervals covers between them, counting an instant once
     * however many of them contain it.
     *
     * @param intervals {@code [from, to)} pairs, in any order; mutated by the sort
     */
    private static long mergedMicros(List<long[]> intervals) {
        intervals.sort(Comparator.comparingLong(interval -> interval[0]));

        long covered = 0;
        long start = intervals.getFirst()[0];
        long end = intervals.getFirst()[1];
        for (long[] interval : intervals) {
            if (interval[0] > end) {
                covered += end - start;
                start = interval[0];
                end = interval[1];
            } else {
                end = Math.max(end, interval[1]);
            }
        }
        return covered + (end - start);
    }

    @Override
    public List<SpanInterval> spanIntervals(long traceId, long spanId, boolean selfOnly) {
        List<TraceSpanShape> shapes = traceRepository.shapesOf(traceId, RUN_FOLD_THRESHOLD);
        if (shapes.isEmpty()) {
            return List.of();
        }
        TraceSkeleton skeleton = TraceSkeleton.of(shapes);
        return skeleton.shapeOf(spanId)
                .map(target -> selfOnly
                        ? selfIntervals(target, skeleton.childrenOf(spanId))
                        : inclusiveIntervals(target, skeleton.descendantsOf(spanId)))
                .orElse(List.of());
    }

    /**
     * The span's own window plus the windows of every descendant that ran on another thread, so a
     * flamegraph scoped inclusively shows the work the span caused and not only the work its own
     * thread did. A phase that fans out to sixteen workers spends nearly all of its time on threads
     * other than its own, and a graph of its window alone showed the coordinator waiting.
     * <p>
     * Same-thread descendants are left out: they lie inside the span's own window already. Windows
     * on one thread that overlap or touch are merged, since the sample filter matches inclusively
     * and two adjacent windows would otherwise count the millisecond they share twice.
     */
    private static List<SpanInterval> inclusiveIntervals(
            TraceSpanShape span, List<TraceSpanShape> descendants) {

        Map<Long, List<long[]>> windowsByThread = new LinkedHashMap<>();
        windowsByThread.computeIfAbsent(span.threadHash(), _ -> new ArrayList<>())
                .add(new long[]{toMillis(span.startEpochMicros()), toMillis(endMicrosOf(span))});
        for (TraceSpanShape descendant : descendants) {
            if (descendant.threadHash() == span.threadHash()) {
                continue;
            }
            windowsByThread.computeIfAbsent(descendant.threadHash(), _ -> new ArrayList<>())
                    .add(new long[]{
                            toMillis(descendant.startEpochMicros()), toMillis(endMicrosOf(descendant))});
        }

        List<SpanInterval> intervals = new ArrayList<>();
        for (Map.Entry<Long, List<long[]>> entry : windowsByThread.entrySet()) {
            List<long[]> windows = entry.getValue();
            windows.sort(Comparator.comparingLong(window -> window[0]));
            List<long[]> merged = new ArrayList<>();
            for (long[] window : windows) {
                if (!merged.isEmpty() && window[0] <= merged.getLast()[1] + 1) {
                    merged.getLast()[1] = Math.max(merged.getLast()[1], window[1]);
                } else {
                    merged.add(window);
                }
            }
            for (long[] window : merged) {
                intervals.add(new SpanInterval(entry.getKey(), window[0], window[1]));
            }
        }
        return intervals;
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
                                operation.notificationCount(),
                                operation.urgentNotificationCount(),
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
    public List<TraceNotificationGroupRow> notifications(TraceNotificationListQuery query) {
        return traceRepository.notifications(query).stream()
                .map(group -> new TraceNotificationGroupRow(
                        group.type(),
                        group.severity(),
                        group.category(),
                        group.source(),
                        group.message(),
                        group.count(),
                        group.traceCount(),
                        group.firstMillisFromBeginning(),
                        group.lastMillisFromBeginning(),
                        group.exemplarTraceIds().stream().map(TraceManagerImpl::toHex).toList()))
                .toList();
    }

    @Override
    public TraceOperationSummary operationSummary(TraceOperationId operation, int spanLimit) {
        List<TraceOperationSpanRow> spans = traceRepository.spanBreakdownOfOperation(operation, spanLimit).stream()
                .map(span -> new TraceOperationSpanRow(
                        span.name(),
                        span.eventType(),
                        span.occurrences(),
                        span.traceCount(),
                        span.totalNanos(),
                        span.selfNanos(),
                        span.p50Nanos(),
                        span.p50SelfNanos(),
                        span.p99Nanos(),
                        span.p99SelfNanos(),
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
        return traceRepository.spanOf(traceId, spanId)
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
    private static List<SpanInterval> selfIntervals(TraceSpanShape span, List<TraceSpanShape> children) {
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
    private static List<TraceSpanShape> sameThreadAs(TraceSpanShape span, List<TraceSpanShape> children) {
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
            TraceSpanShape span, List<TraceSpanShape> children) {

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

    private static long endMicrosOf(TraceSpanRecord span) {
        return span.startEpochMicros() + span.durationNanos() / NANOS_PER_MICRO;
    }

    private static long endMicrosOf(TraceSpanShape span) {
        return TraceSkeleton.endMicrosOf(span);
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
                // Hex like every other id-shaped value on the wire -- the notification and
                // exception rows already render theirs this way, and a frontend comparing the
                // three must find one base, not two.
                toHex(span.threadHash()),
                span.threadName(),
                span.isVirtual(),
                span.eventType(),
                span.attributes(),
                span.eventFields(),
                span.synthesized(),
                span.ioOrigin());
    }

    /**
     * Ids cross the wire as 16-char hex: a 64-bit value exceeds JavaScript's safe integer range, and
     * hex is also how every other tracer renders them.
     */
    private static TraceNotificationRow toRow(TraceNotificationRecord notification) {
        Long spanId = notification.spanId();
        return new TraceNotificationRow(
                spanId == null ? null : toHex(spanId),
                toHex(notification.notificationId()),
                notification.startMillisFromBeginning(),
                notification.startEpochMicros(),
                notification.type(),
                notification.message(),
                notification.severity(),
                notification.category(),
                notification.source(),
                notification.attributes(),
                toHex(notification.threadHash()));
    }

    @Override
    public TraceStacktrace stacktrace(long stacktraceId) {
        List<TraceStackFrameRow> frames = traceRepository.stacktraceOf(stacktraceId).stream()
                .map(TraceManagerImpl::toRow)
                .toList();

        return new TraceStacktrace(toHex(stacktraceId), frames);
    }

    /**
     * A line number of zero is JFR saying it had none rather than saying line zero, and the same
     * goes for a bytecode index; carrying the zero through would have the UI render
     * {@code Foo.java:0} as though it were a location.
     */
    private static TraceStackFrameRow toRow(EventFrame frame) {
        long line = frame.line();
        return new TraceStackFrameRow(
                frame.clazz(),
                frame.method(),
                frame.type(),
                line > 0 ? (int) line : null);
    }

    private static TraceExceptionRow toRow(TraceExceptionRecord exception) {
        Long stacktraceHash = exception.stacktraceHash();
        return new TraceExceptionRow(
                toHex(exception.spanId()),
                toHex(exception.exceptionId()),
                exception.startMillisFromBeginning(),
                exception.startEpochMicros(),
                exception.eventType(),
                exception.thrownClass(),
                exception.message(),
                exception.escaped(),
                stacktraceHash == null ? null : toHex(stacktraceHash),
                toHex(exception.threadHash()));
    }

    private static String toHex(long id) {
        return TraceIds.hex(id);
    }
}
