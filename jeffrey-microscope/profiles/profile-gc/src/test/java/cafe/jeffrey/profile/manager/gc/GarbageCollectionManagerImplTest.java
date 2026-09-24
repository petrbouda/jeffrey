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

package cafe.jeffrey.profile.manager.gc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.profile.manager.model.gc.GCOverviewData;
import cafe.jeffrey.profile.manager.model.gc.GCTimeseriesType;
import cafe.jeffrey.provider.profile.api.ProfileEventRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GarbageCollectionManagerImpl")
class GarbageCollectionManagerImplTest {

    @Mock
    ProfileInfo profileInfo;

    @Mock
    ProfileEventRepository eventRepository;

    @Mock
    ProfileEventStreamRepository eventStreamRepository;

    private GarbageCollectionManagerImpl manager;

    @BeforeEach
    void setUp() {
        manager = new GarbageCollectionManagerImpl(profileInfo, eventRepository, eventStreamRepository);
    }

    @Nested
    @DisplayName("Recording without jdk.GCConfiguration (async-profiler without jfrsync)")
    class WithoutGcConfiguration {

        @BeforeEach
        void noGcConfiguration() {
            when(eventRepository.eventsByTypeWithFields(Type.GC_CONFIGURATION)).thenReturn(List.of());
        }

        @Test
        @DisplayName("The collector type is empty instead of an exception")
        void collectorTypeIsEmpty() {
            assertTrue(manager.garbageCollectorType().isEmpty());
        }

        @Test
        @DisplayName("The overview is empty and no events are streamed")
        void overviewIsEmpty() {
            GCOverviewData overview = manager.overviewData();

            assertEquals(0, overview.header().totalCollections());
            assertTrue(overview.longestPauses().isEmpty());
            assertTrue(overview.pauseDistribution().buckets().isEmpty());
            verifyNoInteractions(eventStreamRepository);
        }

        @Test
        @DisplayName("The pause timeseries is empty and no events are streamed")
        void timeseriesIsEmpty() {
            TimeseriesData timeseries = manager.timeseries(GCTimeseriesType.MAX_PAUSE);

            assertEquals(TimeseriesData.empty(), timeseries);
            verifyNoInteractions(eventStreamRepository);
        }
    }
}
