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

package cafe.jeffrey.profile.manager.heapdump;

import cafe.jeffrey.profile.heapdump.model.ClassDiffEntry;
import cafe.jeffrey.profile.heapdump.model.ClassHistogramEntry;
import cafe.jeffrey.profile.heapdump.model.HeapDumpDiffReport;
import cafe.jeffrey.profile.heapdump.model.HeapSummary;
import cafe.jeffrey.profile.heapdump.model.SortBy;
import cafe.jeffrey.shared.common.exception.JeffreyException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HeapDumpDiffServiceTest {

    @Mock
    HeapDumpManager primary;

    @Mock
    HeapDumpManager baseline;

    private void ready(HeapDumpManager manager) {
        when(manager.heapDumpExists()).thenReturn(true);
        when(manager.isCacheReady()).thenReturn(true);
    }

    private static HeapSummary summary(long bytes, long instances) {
        return new HeapSummary(bytes, instances, 10, 5, Instant.ofEpochMilli(1L));
    }

    private static ClassHistogramEntry entry(String className, long count, long size) {
        return new ClassHistogramEntry(className, count, size, List.of());
    }

    @Nested
    class Diffing {

        @Test
        void computesPerClassDeltasWithNewAndRemovedClasses() {
            ready(primary);
            ready(baseline);
            when(primary.getSummary()).thenReturn(summary(2_000L, 30L));
            when(baseline.getSummary()).thenReturn(summary(1_000L, 20L));
            when(primary.getClassHistogram(anyInt(), eq(SortBy.SIZE))).thenReturn(List.of(
                    entry("com.app.Grown", 20, 1_200L),
                    entry("com.app.New", 5, 500L),
                    entry("com.app.Stable", 5, 300L)));
            when(baseline.getClassHistogram(anyInt(), eq(SortBy.SIZE))).thenReturn(List.of(
                    entry("com.app.Grown", 10, 600L),
                    entry("com.app.Removed", 3, 100L),
                    entry("com.app.Stable", 5, 300L)));

            HeapDumpDiffReport report = HeapDumpDiffService.diff(primary, baseline, 50);

            assertEquals(10L, report.instanceCountDelta());
            assertEquals(1_000L, report.shallowBytesDelta());
            // Stable class has no delta and is filtered out.
            assertEquals(3, report.entries().size());

            ClassDiffEntry grown = report.entries().get(0);
            assertEquals("com.app.Grown", grown.className(), "largest |bytes delta| first");
            assertEquals(10L, grown.countDelta());
            assertEquals(600L, grown.bytesDelta());

            ClassDiffEntry added = findEntry(report, "com.app.New");
            assertEquals(0L, added.baselineCount());
            assertEquals(500L, added.bytesDelta());

            ClassDiffEntry removed = findEntry(report, "com.app.Removed");
            assertEquals(0L, removed.primaryCount());
            assertEquals(-100L, removed.bytesDelta());
        }

        @Test
        void capsEntriesAtTopN() {
            ready(primary);
            ready(baseline);
            when(primary.getSummary()).thenReturn(summary(100L, 10L));
            when(baseline.getSummary()).thenReturn(summary(50L, 5L));
            when(primary.getClassHistogram(anyInt(), eq(SortBy.SIZE))).thenReturn(List.of(
                    entry("a.A", 1, 100L),
                    entry("a.B", 1, 50L),
                    entry("a.C", 1, 10L)));
            when(baseline.getClassHistogram(anyInt(), eq(SortBy.SIZE))).thenReturn(List.of());

            HeapDumpDiffReport report = HeapDumpDiffService.diff(primary, baseline, 2);

            assertEquals(2, report.entries().size());
            assertEquals("a.A", report.entries().get(0).className());
        }
    }

    @Nested
    class Validation {

        @Test
        void rejectsUninitializedBaseline() {
            ready(primary);
            when(baseline.heapDumpExists()).thenReturn(true);
            when(baseline.isCacheReady()).thenReturn(false);

            assertThrows(JeffreyException.class,
                    () -> HeapDumpDiffService.diff(primary, baseline, 10));
        }

        @Test
        void rejectsMissingPrimaryDump() {
            when(primary.heapDumpExists()).thenReturn(false);

            assertThrows(JeffreyException.class,
                    () -> HeapDumpDiffService.diff(primary, baseline, 10));
        }

        @Test
        void rejectsNonPositiveTopN() {
            assertThrows(IllegalArgumentException.class,
                    () -> HeapDumpDiffService.diff(primary, baseline, 0));
        }
    }

    private static ClassDiffEntry findEntry(HeapDumpDiffReport report, String className) {
        return report.entries().stream()
                .filter(e -> className.equals(e.className()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing entry for " + className));
    }
}
