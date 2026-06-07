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

import cafe.jeffrey.profile.manager.model.span.SpanDetailRow;
import cafe.jeffrey.profile.manager.model.span.SpanEventRow;
import cafe.jeffrey.profile.manager.model.span.SpanHeatmap;
import cafe.jeffrey.profile.manager.model.span.SpanHeatmapRow;
import cafe.jeffrey.profile.manager.model.span.SpanOverview;
import cafe.jeffrey.profile.manager.model.span.SpanSlowestRow;
import cafe.jeffrey.profile.manager.model.span.SpanTagStat;
import cafe.jeffrey.provider.profile.api.SpanEventRecord;
import cafe.jeffrey.provider.profile.api.SpanRecord;
import cafe.jeffrey.provider.profile.api.SpanRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpanManagerImplTest {

    private static final long MS = 1_000_000L;

    private static SpanRecord span(String tag, long startMillis, long durationMillis, long osThreadId) {
        return new SpanRecord(
                startMillis, startMillis, durationMillis * MS, osThreadId, osThreadId + 100, "thread-" + osThreadId, tag);
    }

    private static SpanManager manager(SpanRecord... spans) {
        return new SpanManagerImpl(new StubSpanRepository(List.of(spans), List.of()));
    }

    private static SpanManager managerWithEvents(List<SpanEventRecord> events) {
        return new SpanManagerImpl(new StubSpanRepository(List.of(), events));
    }

    /** Minimal repository stub (SpanRepository is no longer single-method). */
    private record StubSpanRepository(List<SpanRecord> spans, List<SpanEventRecord> events)
            implements SpanRepository {
        @Override
        public List<SpanRecord> listSpans() {
            return spans;
        }

        @Override
        public List<SpanEventRecord> eventsForThread(long osThreadId, long fromEpochMillis, long toEpochMillis) {
            return events;
        }
    }

    @Nested
    class Overview {

        @Test
        void summarisesAllSpans() {
            SpanOverview overview = manager(
                    span("a", 0, 10, 1),
                    span("a", 20, 30, 1),
                    span("b", 0, 5, 2)).overview();

            assertEquals(3, overview.totalSpans());
            assertEquals(45 * MS, overview.totalNanos());
            assertEquals(15 * MS, overview.avgNanos());
            assertEquals(30 * MS, overview.maxNanos());
            assertEquals(2, overview.distinctTags());
        }

        @Test
        void emptyProfileIsAllZero() {
            SpanOverview overview = manager().overview();
            assertEquals(0, overview.totalSpans());
            assertEquals(0, overview.totalNanos());
            assertEquals(0, overview.distinctTags());
        }
    }

    @Nested
    class TagStatistics {

        @Test
        void aggregatesCountTotalAvgMax() {
            SpanManager manager = manager(
                    span("a", 0, 10, 1),
                    span("a", 20, 30, 1),
                    span("b", 0, 5, 1));

            List<SpanTagStat> stats = manager.tagStatistics();

            // a total = 40ms > b total = 5ms → a first
            assertEquals("a", stats.get(0).tag());
            assertEquals(2, stats.get(0).count());
            assertEquals(40 * MS, stats.get(0).totalNanos());
            assertEquals(20 * MS, stats.get(0).avgNanos());
            assertEquals(30 * MS, stats.get(0).maxNanos());
            assertEquals("b", stats.get(1).tag());
            assertEquals(1, stats.get(1).count());
        }

        @Test
        void p95PicksHighDurationForSkewedSet() {
            // durations (ms): 1..9 + 500. p95 index = ceil(0.95*10)-1 = 9 → the 500ms outlier.
            SpanRecord[] spans = new SpanRecord[10];
            for (int i = 0; i < 9; i++) {
                spans[i] = span("x", i * 10L, i + 1L, 1);
            }
            spans[9] = span("x", 100, 500, 1);

            List<SpanTagStat> stats = manager(spans).tagStatistics();

            assertEquals(500 * MS, stats.get(0).p95Nanos());
            assertEquals(500 * MS, stats.get(0).p99Nanos());
        }

        @Test
        void singleSpanP95EqualsItsDuration() {
            List<SpanTagStat> stats = manager(span("solo", 0, 42, 1)).tagStatistics();
            assertEquals(42 * MS, stats.get(0).p95Nanos());
        }
    }

    @Nested
    class TagSpans {

        @Test
        void returnsOnlyMatchingTagOrderedByStart() {
            List<SpanDetailRow> rows = manager(
                    span("a", 200, 10, 1),
                    span("b", 0, 10, 1),
                    span("a", 0, 30, 2)).tagSpans("a");

            assertEquals(2, rows.size());
            // ordered by start epoch (helper uses startMillis as epoch)
            assertEquals(0, rows.get(0).startEpochMillis());
            assertEquals(30 * MS, rows.get(0).durationNanos());
            assertEquals(2, rows.get(0).osThreadId());
            assertEquals(200, rows.get(1).startEpochMillis());
        }

        @Test
        void unknownTagYieldsEmpty() {
            assertTrue(manager(span("a", 0, 10, 1)).tagSpans("missing").isEmpty());
        }
    }

    @Nested
    class SlowestSpans {

        @Test
        void ordersByDurationDescendingAcrossAllTagsAndCarriesTag() {
            List<SpanSlowestRow> rows = manager(
                    span("a", 0, 10, 1),
                    span("b", 20, 30, 2),
                    span("a", 40, 5, 1)).slowestSpans(10);

            assertEquals(3, rows.size());
            assertEquals(30 * MS, rows.get(0).durationNanos());
            assertEquals("b", rows.get(0).tag());
            assertEquals(2, rows.get(0).osThreadId());
            assertEquals(10 * MS, rows.get(1).durationNanos());
            assertEquals("a", rows.get(1).tag());
            assertEquals(5 * MS, rows.get(2).durationNanos());
        }

        @Test
        void capsResultsToLimit() {
            List<SpanSlowestRow> rows = manager(
                    span("a", 0, 10, 1),
                    span("b", 20, 30, 1),
                    span("c", 40, 20, 1)).slowestSpans(2);

            assertEquals(2, rows.size());
            assertEquals(30 * MS, rows.get(0).durationNanos());
            assertEquals(20 * MS, rows.get(1).durationNanos());
        }

        @Test
        void nullTagBecomesEmptyString() {
            List<SpanSlowestRow> rows = manager(span(null, 0, 10, 1)).slowestSpans(10);
            assertEquals("", rows.get(0).tag());
        }

        @Test
        void emptyProfileYieldsEmpty() {
            assertTrue(manager().slowestSpans(10).isEmpty());
        }
    }

    @Nested
    class SpanEvents {

        @Test
        void mapsRepositoryEventsToRows() {
            List<SpanEventRow> rows = managerWithEvents(List.of(
                    new SpanEventRecord("jdk.ExecutionSample", 1_000, 0, null),
                    new SpanEventRecord("jdk.JavaMonitorEnter", 1_050, 3 * MS, "{\"monitorClass\":\"X\"}")))
                    .spanEvents(41, 0, 5_000);

            assertEquals(2, rows.size());
            assertEquals("jdk.ExecutionSample", rows.get(0).eventType());
            assertEquals(1_000, rows.get(0).startEpochMillis());
            assertEquals(3 * MS, rows.get(1).durationNanos());
            assertEquals("{\"monitorClass\":\"X\"}", rows.get(1).fields());
        }

        @Test
        void emptyWhenNoEvents() {
            assertTrue(managerWithEvents(List.of()).spanEvents(41, 0, 5_000).isEmpty());
        }
    }

    @Nested
    class Heatmap {

        @Test
        void placesSpansIntoTimeBucketsByTag() {
            SpanHeatmap heatmap = manager(
                    span("a", 0, 10, 1),
                    span("a", 10_000, 10, 1),
                    span("b", 0, 10, 1)).heatmap();

            assertEquals(60, heatmap.bucketCount());
            assertEquals(2, heatmap.rows().size());

            SpanHeatmapRow rowA = heatmap.rows().stream()
                    .filter(row -> row.tag().equals("a")).findFirst().orElseThrow();
            // Two 'a' spans far apart → land in two distinct buckets.
            assertEquals(2, rowA.cells().size());
            assertTrue(rowA.cells().get(0).bucket() < rowA.cells().get(1).bucket());
            assertEquals(1, rowA.cells().get(0).count());
        }

        @Test
        void emptyProfileHasNoRows() {
            SpanHeatmap heatmap = manager().heatmap();
            assertTrue(heatmap.rows().isEmpty());
            assertEquals(1, heatmap.bucketMillis());
        }
    }
}
