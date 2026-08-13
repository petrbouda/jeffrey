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
import cafe.jeffrey.profile.manager.model.trace.TraceSpanRow;
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
    private static final long MS = 1_000_000L;

    @Mock
    TraceRepository traceRepository;

    /**
     * @param startMillis start as absolute epoch millis; the fixture uses a 0 origin for readability
     * @param durationMs  duration in milliseconds, converted to the nanos the record stores
     */
    private static TraceSpanRecord span(long spanId, Long parentSpanId, String name,
            long startMillis, long durationMs) {
        return new TraceSpanRecord(
                TRACE, spanId, parentSpanId, name, "INTERNAL", "UNSET", null, "{}",
                startMillis, startMillis, durationMs * MS, 900L, "worker", "jeffrey.TraceSpan");
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
            // Legitimate when the parent hands work to another thread and returns first.
            List<TraceSpanRow> spans = spansOf(List.of(
                    span(1, null, "root", 0, 10),
                    span(2, 1L, "outlives", 0, 50)));

            assertEquals(0, spans.getFirst().selfDurationNanos());
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
            assertEquals(120, intervals.get(0).toEpochMillis());
            assertEquals(130, intervals.get(1).fromEpochMillis());
            assertEquals(150, intervals.get(1).toEpochMillis());
        }

        @Test
        @DisplayName("a leaf's self scope is its whole window")
        void leafSelfIsWholeWindow() {
            TraceManagerImpl manager = managerOf(List.of(span(1, null, "leaf", 100, 50)));

            assertEquals(List.of(new SpanInterval(900L, 100, 150)),
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

        @Test
        @DisplayName("an unknown span yields no intervals rather than failing")
        void unknownSpanIsEmpty() {
            assertTrue(managerOf(List.of(span(1, null, "root", 0, 10)))
                    .spanIntervals(TRACE, 404, false).isEmpty());
        }
    }

    @Test
    @DisplayName("a trace with no spans is absent, not an empty trace")
    void unknownTraceIsEmpty() {
        when(traceRepository.spansOf(TRACE)).thenReturn(List.of());

        assertEquals(Optional.empty(), new TraceManagerImpl(traceRepository).trace(TRACE));
    }
}
