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
import cafe.jeffrey.profile.manager.model.trace.TraceOverview;
import cafe.jeffrey.profile.manager.model.trace.TraceSpanRow;
import cafe.jeffrey.provider.profile.api.TraceOverviewRecord;
import cafe.jeffrey.provider.profile.api.TraceRepository;
import cafe.jeffrey.provider.profile.api.TraceSpanRecord;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TraceManagerImplTest {

    private static final long TRACE = 7L;
    private static final long OTHER_TRACE = 8L;
    private static final long MS = 1_000_000L;
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
        return new TraceSpanRecord(
                TRACE, spanId, parentSpanId, name, "INTERNAL", "UNSET", null,
                startMillis, startMillis, durationMs * MS, threadHash, "worker", "jeffrey.TraceSpan");
    }

    /** Like {@link #span}, but for a caller building spans from more than one trace. */
    private static TraceSpanRecord spanOnTrace(long traceId, long spanId, Long parentSpanId, String name,
            long startMillis, long durationMs) {
        return new TraceSpanRecord(
                traceId, spanId, parentSpanId, name, "INTERNAL", "UNSET", null,
                startMillis, startMillis, durationMs * MS, THREAD, "worker", "jeffrey.TraceSpan");
    }

    private TraceManagerImpl managerOf(List<TraceSpanRecord> spans) {
        when(traceRepository.spansOf(TRACE)).thenReturn(spans);
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
        @DisplayName("is the span's duration minus what its children covered")
        void subtractsChildren() {
            List<TraceSpanRow> spans = spansOf(List.of(
                    span(1, null, "root", 0, 100),
                    span(2, 1L, "child", 10, 30)));

            assertEquals(70 * MS, spans.getFirst().selfDurationNanos());
            assertEquals(30 * MS, spans.get(1).selfDurationNanos(),
                    "a leaf's self time is its whole duration");
        }

        @Test
        @DisplayName("overlapping children are not subtracted twice")
        void mergesOverlappingChildren() {
            // Two children running concurrently cover 10..50, i.e. 40ms, not 30+30.
            List<TraceSpanRow> spans = spansOf(List.of(
                    span(1, null, "root", 0, 100),
                    span(2, 1L, "a", 10, 30),
                    span(3, 1L, "b", 20, 30)));

            assertEquals(60 * MS, spans.getFirst().selfDurationNanos());
        }

        @Test
        @DisplayName("never goes negative when a child outlives its parent")
        void clampsAtZero() {
            // Malformed but recorded: the child's window is longer than the parent's own.
            List<TraceSpanRow> spans = spansOf(List.of(
                    span(1, null, "root", 0, 10),
                    span(2, 1L, "outlives", 0, 50)));

            assertEquals(0, spans.getFirst().selfDurationNanos());
        }

        @Test
        @DisplayName("a child on another thread is not subtracted from the parent's own time")
        void ignoresChildrenOnOtherThreads() {
            // Tracer.continueIn forks the work: the parent thread kept working the whole time.
            List<TraceSpanRow> spans = spansOf(List.of(
                    span(1, null, "root", 0, 100),
                    spanOnThread(2, 1L, "forked", 10, 30, OTHER_THREAD)));

            assertEquals(100 * MS, spans.getFirst().selfDurationNanos());
        }
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

    @Nested
    @DisplayName("Operation intervals")
    class OperationIntervals {

        private static final String OPERATION = "POST /orders";

        @Test
        @DisplayName("one interval per thread, spanning that thread's work in the trace")
        void groupsByThread() {
            when(traceRepository.spansOfOperation(OPERATION)).thenReturn(List.of(
                    span(1, null, "root", 0, 100),
                    span(2, 1L, "child", 10, 20),
                    spanOnThread(3, 1L, "async", 60, 20, OTHER_THREAD)));

            List<SpanInterval> intervals = new TraceManagerImpl(traceRepository)
                    .operationIntervals(OPERATION);

            assertEquals(2, intervals.size(), "the two threads the trace touched");
            SpanInterval main = intervals.stream()
                    .filter(interval -> interval.threadHash() == THREAD).findFirst().orElseThrow();
            assertEquals(0, main.fromEpochMillis());
            assertEquals(100, main.toEpochMillis(), "the root's window covers its same-thread child");

            SpanInterval other = intervals.stream()
                    .filter(interval -> interval.threadHash() == OTHER_THREAD).findFirst().orElseThrow();
            assertEquals(60, other.fromEpochMillis());
            assertEquals(80, other.toEpochMillis());
        }

        @Test
        @DisplayName("two traces of the same operation on the same thread stay separate intervals")
        void doesNotMergeAcrossTraces() {
            // Keying only by threadHash would merge these into one window spanning the idle gap
            // between the traces (100..200), pulling unrelated samples into the flamegraph.
            when(traceRepository.spansOfOperation(OPERATION)).thenReturn(List.of(
                    spanOnTrace(TRACE, 1, null, "root", 0, 100),
                    spanOnTrace(OTHER_TRACE, 1, null, "root", 200, 100)));

            List<SpanInterval> intervals = new TraceManagerImpl(traceRepository)
                    .operationIntervals(OPERATION);

            assertEquals(2, intervals.size(), "one interval per trace, not merged across the gap");
            assertTrue(intervals.contains(new SpanInterval(THREAD, 0, 100)));
            assertTrue(intervals.contains(new SpanInterval(THREAD, 200, 300)));
        }

        @Test
        @DisplayName("an operation with no spans yields no intervals rather than a null window")
        void handlesAnUnknownOperation() {
            when(traceRepository.spansOfOperation("nope")).thenReturn(List.of());

            assertTrue(new TraceManagerImpl(traceRepository).operationIntervals("nope").isEmpty());
        }
    }
}
