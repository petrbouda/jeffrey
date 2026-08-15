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

import cafe.jeffrey.profile.manager.model.trace.TimelineSpan;
import cafe.jeffrey.profile.manager.model.trace.TimelineTrack;
import cafe.jeffrey.profile.manager.model.trace.TimelineWindow;
import cafe.jeffrey.profile.manager.model.trace.TraceDetail;
import cafe.jeffrey.profile.manager.model.trace.TraceOverview;
import cafe.jeffrey.profile.manager.model.trace.TraceRow;
import cafe.jeffrey.profile.manager.model.trace.TraceSpanEvents;
import cafe.jeffrey.profile.manager.model.trace.TraceSpanRow;
import cafe.jeffrey.provider.profile.api.ThreadWindowEventRecord;
import cafe.jeffrey.provider.profile.api.ThreadWindowEventsPage;
import cafe.jeffrey.provider.profile.api.TraceContextCategory;
import cafe.jeffrey.provider.profile.api.TraceOverviewRecord;
import cafe.jeffrey.provider.profile.api.TraceRepository;
import cafe.jeffrey.provider.profile.api.TraceSpanRecord;
import cafe.jeffrey.provider.profile.api.TraceSummaryRecord;
import cafe.jeffrey.provider.profile.api.TraceThreadStateRecord;
import cafe.jeffrey.shared.common.model.SpanInterval;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TraceManagerImplTest {

    private static final long TRACE = 7L;
    private static final long OTHER_TRACE = 8L;
    private static final long MS = 1_000_000L;
    private static final long US = 1_000L;
    private static final long MICROS_PER_MILLI = 1_000L;
    private static final long THREAD = 900L;
    private static final long OTHER_THREAD = 901L;

    @Mock
    TraceRepository traceRepository;

    /**
     * @param startMillis start as absolute epoch millis; the fixture uses a 0 origin for readability
     * @param durationMs  duration in milliseconds, converted to the nanos the record stores
     */
    private static TraceSpanRecord span(long spanId, Long parentSpanId, String name,
            long startMillis, long durationMs) {
        return spanOnThread(spanId, parentSpanId, name, startMillis, durationMs, THREAD);
    }

    private static TraceSpanRecord spanOnThread(long spanId, Long parentSpanId, String name,
            long startMillis, long durationMs, long threadHash) {
        return microSpan(TRACE, spanId, parentSpanId, name,
                startMillis * MICROS_PER_MILLI, durationMs * MS, threadHash);
    }

    /**
     * A span given directly in the units the manager works in, for the cases whole milliseconds
     * cannot express: spans shorter than a millisecond, and spans a fraction of one apart.
     *
     * @param startMicros   start as absolute epoch micros
     * @param durationMicros duration in microseconds, converted to the nanos the record stores
     */
    private static TraceSpanRecord spanMicros(long spanId, Long parentSpanId, String name,
            long startMicros, long durationMicros) {
        return microSpan(TRACE, spanId, parentSpanId, name, startMicros, durationMicros * US, THREAD);
    }

    /**
     * Self time defaults to the whole duration, which is what the derivation stores for a childless
     * span. The manager only passes it through, so the tree tests do not care what it holds; the one
     * test that does supplies it through {@link #spanWithSelf}.
     */
    private static TraceSpanRecord microSpan(long traceId, long spanId, Long parentSpanId, String name,
            long startMicros, long durationNanos, long threadHash) {
        return microSpan(traceId, spanId, parentSpanId, name, startMicros, durationNanos, threadHash,
                durationNanos);
    }

    private static TraceSpanRecord microSpan(long traceId, long spanId, Long parentSpanId, String name,
            long startMicros, long durationNanos, long threadHash, long selfDurationNanos) {
        return new TraceSpanRecord(
                traceId, spanId, parentSpanId, name, "INTERNAL", "UNSET", null,
                startMicros / MICROS_PER_MILLI, startMicros, durationNanos, selfDurationNanos,
                threadHash, "worker", false, "jeffrey.TraceSpan", null, null);
    }

    /** A span carrying a self time of its own, in milliseconds like the rest of the fixture. */
    private static TraceSpanRecord spanWithSelf(long spanId, Long parentSpanId, String name,
            long startMillis, long durationMs, long selfMs) {
        return microSpan(TRACE, spanId, parentSpanId, name,
                startMillis * MICROS_PER_MILLI, durationMs * MS, THREAD, selfMs * MS);
    }

    /** Like {@link #span}, but for a caller building spans from more than one trace. */
    private static TraceSpanRecord spanOnTrace(long traceId, long spanId, Long parentSpanId, String name,
            long startMillis, long durationMs) {
        return microSpan(traceId, spanId, parentSpanId, name,
                startMillis * MICROS_PER_MILLI, durationMs * MS, THREAD);
    }

    private TraceManagerImpl managerOf(List<TraceSpanRecord> spans) {
        when(traceRepository.spansOf(TRACE)).thenReturn(spans);
        // The header comes from the traces table rather than being recomputed from these spans, so a
        // manager test has to supply it. Its values do not matter to the tree assembly under test.
        lenient().when(traceRepository.summaryOf(TRACE)).thenReturn(Optional.of(new TraceSummaryRecord(
                TRACE, "root", "INTERNAL", "jeffrey.TraceSpan", 0, 0, 0, spans.size(), 0, true)));
        return new TraceManagerImpl(traceRepository);
    }

    private List<TraceSpanRow> spansOf(List<TraceSpanRecord> records) {
        return managerOf(records).trace(TRACE).map(TraceDetail::spans).orElseThrow();
    }

    @Nested
    @DisplayName("Tree assembly")
    class TreeAssembly {

        @Test
        @DisplayName("orders depth-first from the root, siblings by start time")
        void ordersDepthFirst() {
            List<TraceSpanRow> spans = spansOf(List.of(
                    span(1, null, "root", 0, 100),
                    span(3, 1L, "second-child", 50, 20),
                    span(2, 1L, "first-child", 10, 20),
                    span(4, 2L, "grandchild", 12, 5)));

            assertEquals(List.of("root", "first-child", "grandchild", "second-child"),
                    spans.stream().map(TraceSpanRow::name).toList());
            assertEquals(List.of(0, 1, 2, 1), spans.stream().map(TraceSpanRow::depth).toList());
        }

        @Test
        @DisplayName("a span whose parent was never recorded is promoted to a root")
        void promotesOrphans() {
            // The parent (id 99) is below the event threshold and never made it into the recording.
            List<TraceSpanRow> spans = spansOf(List.of(
                    span(1, null, "root", 0, 100),
                    span(2, 99L, "orphan", 10, 20)));

            assertEquals(2, spans.size(), "the orphan must not disappear");
            TraceSpanRow orphan = spans.stream()
                    .filter(row -> "orphan".equals(row.name())).findFirst().orElseThrow();
            assertEquals(0, orphan.depth());
            assertNull(orphan.parentSpanId());
        }

        @Test
        @DisplayName("a parent cycle terminates instead of hanging")
        void survivesCycles() {
            // Cannot arise from correct instrumentation, but must not spin if it ever does.
            List<TraceSpanRow> spans = spansOf(List.of(
                    span(1, 2L, "a", 0, 10),
                    span(2, 1L, "b", 1, 5)));

            assertEquals(2, spans.size());
        }

        @Test
        @DisplayName("a span that points at itself is treated as a root")
        void survivesSelfParent() {
            assertEquals(1, spansOf(List.of(span(1, 1L, "self", 0, 10))).size());
        }

        @Test
        @DisplayName("rows sharing a span id place one of them instead of failing")
        void survivesRepeatedSpanIds() {
            // Derivation is what guarantees ids are unique, so this cannot arise from spans the
            // repository builds today. It did once -- every JDBC statement carried its enclosing
            // span's id -- and the trace detail must render rather than fail if it ever does again.
            List<TraceSpanRow> spans = spansOf(List.of(
                    span(1, null, "root", 0, 100),
                    span(2, 1L, "twin", 10, 20),
                    span(2, 1L, "twin", 40, 20)));

            assertEquals(List.of("root", "twin"), spans.stream().map(TraceSpanRow::name).toList());
        }

        @Test
        @DisplayName("ids and thread hash cross the wire as strings")
        void rendersIdsAsHex() {
            TraceSpanRow root = spansOf(List.of(span(255, null, "root", 0, 10))).getFirst();

            assertEquals("00000000000000ff", root.spanId());
            assertEquals("900", root.threadHash());
        }
    }

    @Nested
    @DisplayName("Self time")
    class SelfTime {

        @Test
        @DisplayName("is reported as the derivation stored it")
        void readsTheStoredValue() {
            // The interval merge behind this number runs once, in SQL, where every span of a trace
            // is in hand -- see JdbcTraceRepositoryTest.SelfDuration for the rules it obeys. The
            // manager's job is only to pass it through without recomputing a second answer.
            List<TraceSpanRow> spans = spansOf(List.of(
                    spanWithSelf(1, null, "root", 0, 100, 70),
                    spanWithSelf(2, 1L, "child", 10, 30, 30)));

            assertEquals(70 * MS, spans.getFirst().selfDurationNanos());
            assertEquals(30 * MS, spans.get(1).selfDurationNanos());
        }
    }

    @Nested
    @DisplayName("Timeline window")
    class Timeline {

        private static final long US = 1_000L;

        private TimelineWindow windowOf(List<TraceSpanRecord> spans) {
            when(traceRepository.spansInWindow(anyLong(), anyLong(), anyInt())).thenReturn(spans);
            lenient().when(traceRepository.pausesInWindow(anyLong(), anyLong())).thenReturn(List.of());
            return new TraceManagerImpl(traceRepository).timelineWindow(0, 1_000_000, 4_000);
        }

        /** A span given directly in microseconds on a named thread, since lanes are per thread. */
        private static TraceSpanRecord onThread(
                long spanId, String name, long startMicros, long durationMicros, long threadHash) {

            return new TraceSpanRecord(
                    TRACE, spanId, null, name, "INTERNAL", "UNSET", null,
                    startMicros / MICROS_PER_MILLI, startMicros, durationMicros * US,
                    durationMicros * US, threadHash, "thread-" + threadHash, false,
                    "jeffrey.TraceSpan", null, null);
        }

        @Test
        @DisplayName("groups spans into one track per thread")
        void groupsByThread() {
            TimelineWindow window = windowOf(List.of(
                    onThread(1, "a", 0, 100, THREAD),
                    onThread(2, "b", 10, 20, OTHER_THREAD)));

            assertEquals(2, window.tracks().size());
        }

        @Test
        @DisplayName("nests an enclosed span one lane deeper")
        void nestsEnclosedSpans() {
            TimelineWindow window = windowOf(List.of(
                    onThread(1, "outer", 0, 100, THREAD),
                    onThread(2, "inner", 10, 20, THREAD),
                    onThread(3, "innermost", 12, 5, THREAD)));

            List<TimelineSpan> spans = window.tracks().getFirst().spans();
            assertEquals(List.of(0, 1, 2), spans.stream().map(TimelineSpan::depth).toList());
            assertEquals(3, window.tracks().getFirst().laneCount());
        }

        @Test
        @DisplayName("puts sequential spans back on the same lane")
        void reusesLanesAfterASpanEnds() {
            // Two unrelated requests served one after the other by the same thread. Neither ran
            // inside the other, so indenting the second would claim a nesting that never happened.
            TimelineWindow window = windowOf(List.of(
                    onThread(1, "first", 0, 50, THREAD),
                    onThread(2, "second", 60, 50, THREAD)));

            assertEquals(List.of(0, 0),
                    window.tracks().getFirst().spans().stream().map(TimelineSpan::depth).toList());
            assertEquals(1, window.tracks().getFirst().laneCount());
        }

        @Test
        @DisplayName("a span abutting the previous one does not nest under it")
        void treatsTouchingSpansAsSiblings() {
            TimelineWindow window = windowOf(List.of(
                    onThread(1, "first", 0, 50, THREAD),
                    onThread(2, "second", 50, 50, THREAD)));

            assertEquals(List.of(0, 0),
                    window.tracks().getFirst().spans().stream().map(TimelineSpan::depth).toList());
        }

        @Test
        @DisplayName("depth is per thread, so a forked child starts its own lane")
        void depthIsPerThread() {
            // The child runs inside the parent in time but on another thread; on its own track it is
            // the only thing running, so it belongs at lane 0.
            TimelineWindow window = windowOf(List.of(
                    onThread(1, "parent", 0, 100, THREAD),
                    onThread(2, "forked", 10, 20, OTHER_THREAD)));

            TimelineTrack forked = window.tracks().stream()
                    .filter(track -> track.spans().getFirst().name().equals("forked"))
                    .findFirst().orElseThrow();
            assertEquals(0, forked.spans().getFirst().depth());
        }

        @Test
        @DisplayName("says when the span cap was reached rather than drawing a partial picture")
        void reportsTruncation() {
            List<TraceSpanRecord> many = new java.util.ArrayList<>();
            for (int i = 0; i < 4_000; i++) {
                many.add(onThread(i, "s" + i, i * 10L, 5, THREAD));
            }

            assertTrue(windowOf(many).truncated());
        }

        @Test
        @DisplayName("a quiet window is empty rather than absent")
        void handlesEmptyWindow() {
            TimelineWindow window = windowOf(List.of());

            assertTrue(window.tracks().isEmpty());
            assertFalse(window.truncated());
            assertFalse(window.statesTruncated());
        }

        @Test
        @DisplayName("attaches a thread's waits to that thread's track")
        void attachesStatesToTheirTrack() {
            lenient().when(traceRepository.threadStatesInWindow(anyLong(), anyLong(), anyInt()))
                    .thenReturn(List.of(
                            new TraceThreadStateRecord(
                                    THREAD, TraceContextCategory.PARKED, 20, 40),
                            new TraceThreadStateRecord(
                                    OTHER_THREAD, TraceContextCategory.SOCKET_IO, 30, 35)));

            TimelineWindow window = windowOf(List.of(
                    onThread(1, "a", 0, 100, THREAD),
                    onThread(2, "b", 10, 20, OTHER_THREAD)));

            TimelineTrack first = window.tracks().stream()
                    .filter(track -> track.spans().getFirst().name().equals("a"))
                    .findFirst().orElseThrow();
            assertEquals(1, first.states().size());
            assertEquals("PARKED", first.states().getFirst().category());
            assertEquals(20, first.states().getFirst().startEpochMicros());
            assertEquals(20 * US, first.states().getFirst().durationNanos());
        }

        @Test
        @DisplayName("drops a wait on a thread with no spans in the window")
        void dropsStatesWithoutATrack() {
            // A thread that only waited has no track to underlay; the whole-JVM idle picture is the
            // threads timeline's job, not this view's.
            lenient().when(traceRepository.threadStatesInWindow(anyLong(), anyLong(), anyInt()))
                    .thenReturn(List.of(new TraceThreadStateRecord(
                            OTHER_THREAD, TraceContextCategory.PARKED, 20, 40)));

            TimelineWindow window = windowOf(List.of(onThread(1, "a", 0, 100, THREAD)));

            assertEquals(1, window.tracks().size());
            assertTrue(window.tracks().getFirst().states().isEmpty());
        }
    }

    @Nested
    @DisplayName("Critical path")
    class CriticalPath {

        private static long totalCriticalNanos(List<TraceSpanRow> spans) {
            return spans.stream().mapToLong(TraceSpanRow::criticalPathNanos).sum();
        }

        private static TraceSpanRow named(List<TraceSpanRow> spans, String name) {
            return spans.stream()
                    .filter(span -> span.name().equals(name))
                    .findFirst()
                    .orElseThrow();
        }

        @Test
        @DisplayName("a lone span owns the whole path")
        void singleSpan() {
            List<TraceSpanRow> spans = spansOf(List.of(span(1, null, "root", 0, 100)));

            assertEquals(100 * MS, spans.getFirst().criticalPathNanos());
        }

        @Test
        @DisplayName("a sequential chain splits the path between parent and child")
        void sequentialChain() {
            List<TraceSpanRow> spans = spansOf(List.of(
                    span(1, null, "root", 0, 100),
                    span(2, 1L, "child", 10, 30)));

            assertEquals(70 * MS, named(spans, "root").criticalPathNanos(),
                    "the root owns everything its child was not holding open");
            assertEquals(30 * MS, named(spans, "child").criticalPathNanos());
            assertEquals(100 * MS, totalCriticalNanos(spans),
                    "the path accounts for the trace end to end");
        }

        @Test
        @DisplayName("of two children, the one finishing last is what the parent waited for")
        void laterFinishingSiblingWins() {
            // Both run under the root; b is still going when a is done, so b is what held the root.
            List<TraceSpanRow> spans = spansOf(List.of(
                    span(1, null, "root", 0, 100),
                    spanOnThread(2, 1L, "a", 10, 30, OTHER_THREAD),
                    spanOnThread(3, 1L, "b", 20, 40, OTHER_THREAD)));

            assertEquals(40 * MS, named(spans, "b").criticalPathNanos(),
                    "b covered 20..60 with nothing finishing later");
            assertEquals(10 * MS, named(spans, "a").criticalPathNanos(),
                    "only 10..20, the stretch before b started, was a's to hold");
            assertEquals(100 * MS, totalCriticalNanos(spans));
        }

        @Test
        @DisplayName("a child covered by a later-finishing sibling is not on the path at all")
        void coveredSiblingIsNotCritical() {
            // b starts earlier *and* finishes later, so a never determined anything. Ordering the
            // walk by start rather than by end credited a here and skipped b.
            List<TraceSpanRow> spans = spansOf(List.of(
                    span(1, null, "root", 0, 100),
                    spanOnThread(2, 1L, "covered", 20, 30, OTHER_THREAD),
                    spanOnThread(3, 1L, "covering", 10, 60, OTHER_THREAD)));

            assertEquals(0, named(spans, "covered").criticalPathNanos());
            assertEquals(60 * MS, named(spans, "covering").criticalPathNanos());
            assertEquals(40 * MS, named(spans, "root").criticalPathNanos(),
                    "0..10 before the child began and 70..100 after it ended");
            assertEquals(100 * MS, totalCriticalNanos(spans));
        }

        @Test
        @DisplayName("a child outliving its parent is credited only the stretch they shared")
        void clipsChildrenToTheParentWindow() {
            List<TraceSpanRow> spans = spansOf(List.of(
                    span(1, null, "root", 0, 50),
                    span(2, 1L, "overruns", 20, 80)));

            assertEquals(30 * MS, named(spans, "overruns").criticalPathNanos(),
                    "the child covered 20..50 of the root, not 20..100");
            assertEquals(20 * MS, named(spans, "root").criticalPathNanos());
        }

        @Test
        @DisplayName("never exceeds the span's own duration")
        void cappedAtTheSpansDuration() {
            List<TraceSpanRow> spans = spansOf(List.of(
                    spanMicros(1, null, "root", 0, 1_500),
                    spanMicros(2, 1L, "child", 500, 200)));

            for (TraceSpanRow span : spans) {
                assertTrue(span.criticalPathNanos() <= span.durationNanos(),
                        span.name() + " was credited more than it ran for");
            }
        }
    }

    @Nested
    @DisplayName("Span placement")
    class SpanPlacement {

        @Test
        @DisplayName("spans a fraction of a millisecond apart keep distinct starts")
        void keepsSubMillisecondStartsApart() {
            // These two children floor to the same millisecond. Handing the waterfall that floor is
            // what drew two sequential calls on one thread as overlapping bars.
            List<TraceSpanRow> spans = spansOf(List.of(
                    spanMicros(1, null, "root", 0, 2_000),
                    spanMicros(2, 1L, "first", 1_030, 310),
                    spanMicros(3, 1L, "second", 1_950, 40)));

            assertEquals(List.of(0L, 1_030L, 1_950L),
                    spans.stream().map(TraceSpanRow::startEpochMicros).toList());
        }

        // The trace header is no longer rebuilt from these spans — it is the stored `traces` row —
        // so the duration's precision is pinned in JdbcTraceRepositoryTest against real DuckDB.
    }

    @Nested
    @DisplayName("Span intervals for flamegraph scoping")
    class SpanIntervals {

        @Test
        @DisplayName("inclusive scoping covers the whole span")
        void inclusiveCoversWholeSpan() {
            TraceManagerImpl manager = managerOf(List.of(
                    span(1, null, "root", 100, 50),
                    span(2, 1L, "child", 120, 10)));

            List<SpanInterval> intervals = manager.spanIntervals(TRACE, 1, false);

            assertEquals(1, intervals.size());
            assertEquals(100, intervals.getFirst().fromEpochMillis());
            assertEquals(150, intervals.getFirst().toEpochMillis());
        }

        @Test
        @DisplayName("self scoping cuts the children out, leaving the gaps around them")
        void selfCutsOutChildren() {
            TraceManagerImpl manager = managerOf(List.of(
                    span(1, null, "root", 100, 50),
                    span(2, 1L, "child", 120, 10)));

            List<SpanInterval> intervals = manager.spanIntervals(TRACE, 1, true);

            assertEquals(2, intervals.size(), "before the child and after it");
            assertEquals(100, intervals.get(0).fromEpochMillis());
            assertEquals(119, intervals.get(0).toEpochMillis(),
                    "stops a millisecond short of the child, which the sample filter matches inclusively");
            assertEquals(131, intervals.get(1).fromEpochMillis(),
                    "resumes a millisecond after the child, for the same reason");
            assertEquals(150, intervals.get(1).toEpochMillis());
        }

        @Test
        @DisplayName("the millisecond a child starts and ends on belongs to the child alone")
        void childBoundsAreNotSharedWithTheParent() {
            TraceManagerImpl manager = managerOf(List.of(
                    span(1, null, "root", 100, 50),
                    span(2, 1L, "child", 120, 10)));

            List<SpanInterval> parent = manager.spanIntervals(TRACE, 1, true);
            SpanInterval child = manager.spanIntervals(TRACE, 2, false).getFirst();

            assertTrue(parent.stream().noneMatch(interval ->
                            covers(interval, child.fromEpochMillis()) || covers(interval, child.toEpochMillis())),
                    "a sample on either boundary would otherwise be counted in both graphs");
        }

        @Test
        @DisplayName("a child ending one millisecond early leaves that millisecond to the parent")
        void keepsTheLastUncoveredMillisecond() {
            TraceManagerImpl manager = managerOf(List.of(
                    span(1, null, "root", 100, 50),
                    span(2, 1L, "child", 100, 49)));

            assertEquals(List.of(new SpanInterval(THREAD, 150, 150)),
                    manager.spanIntervals(TRACE, 1, true));
        }

        @Test
        @DisplayName("a child running to the parent's end yields no trailing interval")
        void doesNotBuildAnInvertedInterval() {
            TraceManagerImpl manager = managerOf(List.of(
                    span(1, null, "root", 100, 50),
                    span(2, 1L, "child", 140, 10)));

            assertEquals(List.of(new SpanInterval(THREAD, 100, 139)),
                    manager.spanIntervals(TRACE, 1, true));
        }

        @Test
        @DisplayName("a child on another thread never punches a hole in the parent's window")
        void ignoresChildrenOnOtherThreads() {
            TraceManagerImpl manager = managerOf(List.of(
                    span(1, null, "root", 100, 50),
                    spanOnThread(2, 1L, "forked", 120, 10, OTHER_THREAD)));

            assertEquals(List.of(new SpanInterval(THREAD, 100, 150)),
                    manager.spanIntervals(TRACE, 1, true),
                    "the parent's thread was busy with its own work the whole time");
        }

        @Test
        @DisplayName("a leaf's self scope is its whole window")
        void leafSelfIsWholeWindow() {
            TraceManagerImpl manager = managerOf(List.of(span(1, null, "leaf", 100, 50)));

            assertEquals(List.of(new SpanInterval(THREAD, 100, 150)),
                    manager.spanIntervals(TRACE, 1, true));
        }

        @Test
        @DisplayName("a span fully covered by a child scopes to nothing")
        void fullyCoveredSpanHasNoSelfTime() {
            TraceManagerImpl manager = managerOf(List.of(
                    span(1, null, "root", 100, 50),
                    span(2, 1L, "child", 100, 50)));

            assertTrue(manager.spanIntervals(TRACE, 1, true).isEmpty());
        }

        private static boolean covers(SpanInterval interval, long epochMillis) {
            return epochMillis >= interval.fromEpochMillis() && epochMillis <= interval.toEpochMillis();
        }

        @Test
        @DisplayName("an unknown span yields no intervals rather than failing")
        void unknownSpanIsEmpty() {
            assertTrue(managerOf(List.of(span(1, null, "root", 0, 10)))
                    .spanIntervals(TRACE, 404, false).isEmpty());
        }
    }

    @Nested
    @DisplayName("Overview")
    class Overview {

        @Test
        @DisplayName("passes the repository's totals through unchanged")
        void mapsTheRecord() {
            when(traceRepository.overview())
                    .thenReturn(new TraceOverviewRecord(
                            12, 340, 3, 5, 40 * MS, 90 * MS, 110 * MS, 120 * MS, 4500 * MS, 8));

            TraceOverview overview = new TraceManagerImpl(traceRepository).overview();

            assertEquals(12, overview.totalTraces());
            assertEquals(340, overview.totalSpans());
            assertEquals(3, overview.errorTraces());
            assertEquals(5, overview.errorSpans(),
                    "failed spans are counted apart from the traces carrying them");
            assertEquals(40 * MS, overview.avgNanos());
            assertEquals(90 * MS, overview.p95Nanos());
            assertEquals(110 * MS, overview.p99Nanos());
            assertEquals(120 * MS, overview.maxNanos());
            assertEquals(4500 * MS, overview.totalNanos());
            assertEquals(8, overview.distinctOperations());
        }

        @Test
        @DisplayName("an untraced profile reports zeros rather than failing")
        void untracedProfileIsZeroed() {
            when(traceRepository.overview()).thenReturn(TraceOverviewRecord.EMPTY);

            assertEquals(new TraceOverview(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
                    new TraceManagerImpl(traceRepository).overview());
        }
    }

    @Test
    @DisplayName("a trace with no spans is absent, not an empty trace")
    void unknownTraceIsEmpty() {
        when(traceRepository.spansOf(TRACE)).thenReturn(List.of());

        assertEquals(Optional.empty(), new TraceManagerImpl(traceRepository).trace(TRACE));
    }

    // Operation intervals are reduced in SQL now, not here, so what used to be asserted against a
    // mocked span list is asserted against real DuckDB in JdbcTraceRepositoryTest.OperationIntervals.

    @Nested
    @DisplayName("Events in a span")
    class EventsInSpan {

        @Test
        @DisplayName("a truncated window says so rather than passing the page off as complete")
        void propagatesTruncation() {
            when(traceRepository.spansOf(TRACE)).thenReturn(List.of(span(1, null, "root", 0, 100)));
            when(traceRepository.eventsInSpan(THREAD, 0, 100)).thenReturn(new ThreadWindowEventsPage(
                    List.of(new ThreadWindowEventRecord("jdk.ExecutionSample", 5, MS, "{}")), true));

            TraceSpanEvents events = new TraceManagerImpl(traceRepository).eventsInSpan(TRACE, 1);

            assertTrue(events.truncated(), "the flag is what the UI's notice hangs off");
            assertEquals(1, events.events().size());
            assertEquals("jdk.ExecutionSample", events.events().getFirst().eventType());
        }

        @Test
        @DisplayName("an unknown span yields an empty page rather than failing")
        void unknownSpanIsEmpty() {
            when(traceRepository.spansOf(TRACE)).thenReturn(List.of());

            assertEquals(TraceSpanEvents.EMPTY, new TraceManagerImpl(traceRepository).eventsInSpan(TRACE, 1));
        }
    }
}
