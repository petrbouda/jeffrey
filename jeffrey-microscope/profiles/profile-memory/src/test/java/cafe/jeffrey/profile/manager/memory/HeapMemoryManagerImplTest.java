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

package cafe.jeffrey.profile.manager.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.profile.manager.model.heap.HeapMemoryTimeseriesType;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.api.ProfileEventRepository;
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
    ProfileEventRepository eventRepository;

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
            var manager = new HeapMemoryManagerImpl(PROFILE_INFO, eventRepository, eventStreamRepository);
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

            var manager = new HeapMemoryManagerImpl(PROFILE_INFO, eventRepository, eventStreamRepository);
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

            var manager = new HeapMemoryManagerImpl(PROFILE_INFO, eventRepository, eventStreamRepository);
            SingleSerie result = manager.timeseries(HeapMemoryTimeseriesType.ALLOCATION);

            assertSame(mockSerie, result);
            verify(eventStreamRepository).genericStreaming(any(EventQueryConfigurer.class), any(RecordBuilder.class));
        }

        @Test
        @DisplayName("ALLOCATION streams only the TLAB pair when TLAB events are present")
        void allocationPrefersTlabEvents() {
            when(eventStreamRepository.genericStreaming(any(EventQueryConfigurer.class), any(RecordBuilder.class)))
                    .thenReturn(new SingleSerie("test", List.of()));
            when(eventRepository.containsEventType(Type.OBJECT_ALLOCATION_IN_NEW_TLAB)).thenReturn(true);

            var manager = new HeapMemoryManagerImpl(PROFILE_INFO, eventRepository, eventStreamRepository);
            manager.timeseries(HeapMemoryTimeseriesType.ALLOCATION);

            ArgumentCaptor<EventQueryConfigurer> captor = ArgumentCaptor.forClass(EventQueryConfigurer.class);
            verify(eventStreamRepository).genericStreaming(captor.capture(), any(RecordBuilder.class));
            assertEquals(HeapMemoryManagerImpl.TLAB_ALLOCATION_EVENT_TYPES, captor.getValue().eventTypes());
        }

        @Test
        @DisplayName("ALLOCATION falls back to ObjectAllocationSample when TLAB events are absent")
        void allocationFallsBackToSampledEvents() {
            when(eventStreamRepository.genericStreaming(any(EventQueryConfigurer.class), any(RecordBuilder.class)))
                    .thenReturn(new SingleSerie("test", List.of()));
            when(eventRepository.containsEventType(Type.OBJECT_ALLOCATION_IN_NEW_TLAB)).thenReturn(false);
            when(eventRepository.containsEventType(Type.OBJECT_ALLOCATION_OUTSIDE_TLAB)).thenReturn(false);

            var manager = new HeapMemoryManagerImpl(PROFILE_INFO, eventRepository, eventStreamRepository);
            manager.timeseries(HeapMemoryTimeseriesType.ALLOCATION);

            ArgumentCaptor<EventQueryConfigurer> captor = ArgumentCaptor.forClass(EventQueryConfigurer.class);
            verify(eventStreamRepository).genericStreaming(captor.capture(), any(RecordBuilder.class));
            assertEquals(HeapMemoryManagerImpl.SAMPLED_ALLOCATION_EVENT_TYPES, captor.getValue().eventTypes());
        }
    }
}
