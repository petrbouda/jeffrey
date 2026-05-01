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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.profile.manager.model.heap.HeapMemoryTimeseriesType;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.timeseries.SingleSerie;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("HeapMemoryManagerImpl")
class HeapMemoryManagerImplTest {

    @Mock
    ProfileEventStreamRepository eventStreamRepository;

    private static final Instant START = Instant.parse("2024-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2024-01-01T00:01:00Z");

    private static final ProfileInfo PROFILE_INFO = new ProfileInfo(
            "test-id", "project-1", "workspace-1", "test-profile",
            null, START, END, START, true, false, null);

    @Nested
    @DisplayName("getOverviewData()")
    class OverviewData {

        @Test
        @DisplayName("Returns null (not yet implemented)")
        void returnsNull() {
            var manager = new HeapMemoryManagerImpl(PROFILE_INFO, eventStreamRepository);
            assertNull(manager.getOverviewData());
        }
    }

    @Nested
    @DisplayName("timeseries()")
    class Timeseries {

        @Test
        @DisplayName("HEAP_BEFORE_AFTER_GC delegates to genericStreaming")
        void heapBeforeAfterGCDelegates() {
            SingleSerie mockSerie = new SingleSerie("test", List.of());
            when(eventStreamRepository.genericStreaming(any(EventQueryConfigurer.class), any(RecordBuilder.class)))
                    .thenReturn(mockSerie);

            var manager = new HeapMemoryManagerImpl(PROFILE_INFO, eventStreamRepository);
            SingleSerie result = manager.timeseries(HeapMemoryTimeseriesType.HEAP_BEFORE_AFTER_GC);

            assertSame(mockSerie, result);
            verify(eventStreamRepository).genericStreaming(any(EventQueryConfigurer.class), any(RecordBuilder.class));
        }

        @Test
        @DisplayName("ALLOCATION delegates to genericStreaming")
        void allocationDelegates() {
            SingleSerie mockSerie = new SingleSerie("test", List.of());
            when(eventStreamRepository.genericStreaming(any(EventQueryConfigurer.class), any(RecordBuilder.class)))
                    .thenReturn(mockSerie);

            var manager = new HeapMemoryManagerImpl(PROFILE_INFO, eventStreamRepository);
            SingleSerie result = manager.timeseries(HeapMemoryTimeseriesType.ALLOCATION);

            assertSame(mockSerie, result);
            verify(eventStreamRepository).genericStreaming(any(EventQueryConfigurer.class), any(RecordBuilder.class));
        }
    }
}
