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
import cafe.jeffrey.profile.manager.model.nmt.NmtCategory;
import cafe.jeffrey.profile.manager.model.nmt.NmtOverview;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.ProfileEventRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NativeMemoryTrackingManagerImpl")
class NativeMemoryTrackingManagerImplTest {

    @Mock
    ProfileEventRepository eventRepository;

    @Mock
    ProfileEventStreamRepository eventStreamRepository;

    private static final Instant START = Instant.parse("2024-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2024-01-01T00:01:00Z");

    private static final ProfileInfo PROFILE_INFO = new ProfileInfo(
            "test-id", "project-1", "workspace-1", "test-profile",
            null, START, END, START, true, false, null);

    private NativeMemoryTrackingManagerImpl manager() {
        return new NativeMemoryTrackingManagerImpl(PROFILE_INFO, eventRepository, eventStreamRepository);
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

    private static GenericRecord usage(long secondsFromStart, String type, long reserved, long committed) {
        ObjectNode node = Json.createObject();
        node.put("type", type);
        node.put("reserved", reserved);
        node.put("committed", committed);
        return record(Type.NATIVE_MEMORY_USAGE, secondsFromStart, node);
    }

    private static GenericRecord total(long secondsFromStart, long reserved, long committed) {
        ObjectNode node = Json.createObject();
        node.put("reserved", reserved);
        node.put("committed", committed);
        return record(Type.NATIVE_MEMORY_USAGE_TOTAL, secondsFromStart, node);
    }

    private static GenericRecord record(Type type, long secondsFromStart, ObjectNode fields) {
        return new GenericRecord(
                type, "label", START,
                Duration.ofSeconds(secondsFromStart), null,
                null, null, 0L, 0L, fields);
    }

    @Nested
    @DisplayName("categories()")
    class Categories {

        @Test
        @DisplayName("Returns empty without streaming when the event is absent")
        void emptyWhenAbsent() {
            when(eventRepository.containsEventType(Type.NATIVE_MEMORY_USAGE)).thenReturn(false);

            assertTrue(manager().categories().isEmpty());
            verify(eventStreamRepository, never()).genericStreaming(any(), any());
        }

        @Test
        @DisplayName("Groups by category with growth (first to last), ordered by committed desc")
        void groupsWithGrowth() {
            when(eventRepository.containsEventType(Type.NATIVE_MEMORY_USAGE)).thenReturn(true);
            stubStreaming(
                    usage(1, "Thread", 200, 100),
                    usage(1, "Class", 80, 50),
                    usage(2, "Thread", 200, 160));

            List<NmtCategory> categories = manager().categories();

            assertEquals(2, categories.size());
            NmtCategory thread = categories.getFirst();
            assertEquals("Thread", thread.category());
            assertEquals(160, thread.committedBytes());
            assertEquals(200, thread.reservedBytes());
            assertEquals(100, thread.startCommittedBytes());
            assertEquals(60, thread.growthBytes());
            assertEquals("Class", categories.get(1).category());
        }
    }

    @Nested
    @DisplayName("categoryTimeline()")
    class CategoryTimeline {

        @Test
        @DisplayName("Keeps the top categories and collapses the rest into Other")
        void topCategoriesPlusOther() {
            when(eventRepository.containsEventType(Type.NATIVE_MEMORY_USAGE)).thenReturn(true);
            GenericRecord[] records = new GenericRecord[10];
            for (int i = 0; i < 10; i++) {
                // distinct committed sizes so ranking is unambiguous
                records[i] = usage(5, "Category-" + i, 0, (i + 1) * 10L);
            }
            stubStreaming(records);

            TimeseriesData timeline = manager().categoryTimeline();

            // 8 top categories + one "Other" bucket for the remaining 2
            assertEquals(9, timeline.series().size());
            assertEquals("Other", timeline.series().getLast().name());
        }
    }

    @Nested
    @DisplayName("totalTimeline()")
    class TotalTimeline {

        @Test
        @DisplayName("Builds committed and reserved series")
        void buildsSeries() {
            when(eventRepository.containsEventType(Type.NATIVE_MEMORY_USAGE_TOTAL)).thenReturn(true);
            stubStreaming(total(2, 2_000, 1_000));

            TimeseriesData timeline = manager().totalTimeline();

            assertEquals("Committed", timeline.series().get(0).name());
            assertEquals("Reserved", timeline.series().get(1).name());
            long maxCommitted = timeline.series().get(0).data().stream().mapToLong(p -> p.get(1)).max().orElse(0);
            assertEquals(1_000, maxCommitted);
        }
    }

    @Nested
    @DisplayName("overview()")
    class Overview {

        @Test
        @DisplayName("Empty and hasNmtData=false without streaming when no NMT events exist")
        void emptyWhenAbsent() {
            when(eventRepository.containsEventType(any())).thenReturn(false);

            NmtOverview overview = manager().overview();

            assertFalse(overview.hasNmtData());
            assertEquals(0, overview.totalCommittedBytes());
            assertEquals(0, overview.categoryCount());
            verify(eventStreamRepository, never()).genericStreaming(any(), any());
        }

        @Test
        @DisplayName("Sums categories when only per-category data is present")
        void sumsCategoriesWithoutTotalEvent() {
            when(eventRepository.containsEventType(Type.NATIVE_MEMORY_USAGE)).thenReturn(true);
            when(eventRepository.containsEventType(Type.NATIVE_MEMORY_USAGE_TOTAL)).thenReturn(false);
            when(eventRepository.latestJsonFields(Type.RESIDENT_SET_SIZE)).thenReturn(Optional.empty());
            stubStreaming(
                    usage(1, "Thread", 200, 160),
                    usage(1, "Class", 80, 50));

            NmtOverview overview = manager().overview();

            assertTrue(overview.hasNmtData());
            assertEquals(210, overview.totalCommittedBytes());
            assertEquals(280, overview.totalReservedBytes());
            assertEquals(210, overview.peakCommittedBytes());
            assertEquals("Thread", overview.largestCategory());
            assertEquals(160, overview.largestCategoryCommittedBytes());
            assertEquals(2, overview.categoryCount());
            assertEquals(0, overview.untrackedBytes());
        }
    }
}
