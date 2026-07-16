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

package cafe.jeffrey.profile.manager.memory;

import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cafe.jeffrey.profile.manager.model.nativememory.NativeLibraryInfo;
import cafe.jeffrey.profile.manager.model.nativememory.NativeMemoryOverview;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.ProfileEventRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.timeseries.SingleSerie;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NativeMemoryManagerImpl")
class NativeMemoryManagerImplTest {

    @Mock
    ProfileEventRepository eventRepository;

    @Mock
    ProfileEventStreamRepository eventStreamRepository;

    private static final Instant START = Instant.parse("2024-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2024-01-01T00:01:00Z");

    private static final ProfileInfo PROFILE_INFO = new ProfileInfo(
            "test-id", "project-1", "workspace-1", "test-profile",
            null, START, END, START, true, false, null);

    private NativeMemoryManagerImpl manager() {
        return new NativeMemoryManagerImpl(PROFILE_INFO, eventRepository, eventStreamRepository);
    }

    @SuppressWarnings("unchecked")
    private void stubStreaming(GenericRecord... records) {
        when(eventStreamRepository.genericStreaming(any(), any())).thenAnswer(invocation -> {
            RecordBuilder<GenericRecord, Object> builder = invocation.getArgument(1);
            for (GenericRecord record : records) {
                builder.onRecord(record);
            }
            return builder.build();
        });
    }

    private static GenericRecord record(Type type, long secondsFromStart, ObjectNode fields) {
        return new GenericRecord(
                type, "label", START,
                Duration.ofSeconds(secondsFromStart), null,
                null, null, 0L, 0L, fields);
    }

    private static ObjectNode rssFields(long size, long peak) {
        ObjectNode node = Json.createObject();
        node.put("size", size);
        node.put("peak", peak);
        return node;
    }

    private static ObjectNode heapSummaryFields(long heapUsed) {
        ObjectNode node = Json.createObject();
        node.put("heapUsed", heapUsed);
        return node;
    }

    private static ObjectNode libraryFields(String name, long base, long top) {
        ObjectNode node = Json.createObject();
        node.put("name", name);
        node.put("baseAddress", base);
        node.put("topAddress", top);
        return node;
    }

    @Nested
    @DisplayName("overview()")
    class Overview {

        @Test
        @DisplayName("Combines RSS stats, latest direct buffers, and library count")
        void aggregates() {
            ObjectNode buffers = Json.createObject();
            buffers.put("count", 4L);
            buffers.put("memoryUsed", 40_959L);
            buffers.put("totalCapacity", 50_000L);
            when(eventRepository.latestJsonFields(Type.DIRECT_BUFFER_STATISTICS)).thenReturn(Optional.of(buffers));

            // First streaming call computes RSS stats, second builds the library list.
            stubStreaming(
                    record(Type.RESIDENT_SET_SIZE, 0, rssFields(100, 100)),
                    record(Type.RESIDENT_SET_SIZE, 1, rssFields(300, 350)),
                    record(Type.RESIDENT_SET_SIZE, 2, rssFields(250, 350)));

            NativeMemoryOverview overview = manager().overview();

            assertEquals(350, overview.peakRssBytes());
            assertEquals(250, overview.finalRssBytes());
            assertEquals(150, overview.rssGrowthBytes());
            assertEquals(4, overview.directBufferCount());
            assertEquals(40_959, overview.directBufferMemoryUsed());
            assertEquals(50_000, overview.directBufferTotalCapacity());
        }
    }

    @Nested
    @DisplayName("rssTimeline()")
    class RssTimeline {

        @Test
        @DisplayName("Splits RSS and heapUsed events into two series")
        void buildsBothSeries() {
            stubStreaming(
                    record(Type.RESIDENT_SET_SIZE, 1, rssFields(1000, 1000)),
                    record(Type.GC_HEAP_SUMMARY, 1, heapSummaryFields(400)),
                    record(Type.RESIDENT_SET_SIZE, 2, rssFields(1200, 1200)),
                    record(Type.GC_HEAP_SUMMARY, 2, heapSummaryFields(500)));

            TimeseriesData timeline = manager().rssTimeline();

            assertEquals(2, timeline.series().size());
            SingleSerie rss = timeline.series().get(0);
            SingleSerie heap = timeline.series().get(1);
            assertEquals("Resident Set Size", rss.name());
            assertEquals("Heap Used", heap.name());
            assertEquals(1200, maxValue(rss));
            assertEquals(500, maxValue(heap));
        }

        private long maxValue(SingleSerie serie) {
            return serie.data().stream().mapToLong(point -> point.get(1)).max().orElse(0);
        }
    }

    @Nested
    @DisplayName("nativeLibraries()")
    class NativeLibraries {

        @Test
        @DisplayName("Dedupes by name, computes mapped size, sorts descending")
        void dedupesAndSorts() {
            stubStreaming(
                    record(Type.NATIVE_LIBRARY, 0, libraryFields("libsmall.so", 1000, 1500)),
                    record(Type.NATIVE_LIBRARY, 0, libraryFields("libbig.so", 2000, 12_000)),
                    record(Type.NATIVE_LIBRARY, 1, libraryFields("libsmall.so", 1000, 1600)));

            List<NativeLibraryInfo> libraries = manager().nativeLibraries();

            assertEquals(2, libraries.size());
            assertEquals("libbig.so", libraries.getFirst().name());
            assertEquals(10_000, libraries.getFirst().mappedBytes());
            assertEquals(600, libraries.get(1).mappedBytes());
        }
    }
}
