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

package cafe.jeffrey.hub.core.manager.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cafe.jeffrey.hub.core.manager.storage.StorageOverview.InfrastructureUsage;
import cafe.jeffrey.hub.core.manager.storage.StorageOverviewCache.CachedOverview;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorageOverviewCacheTest {

    private static final Instant FIRST_TICK = Instant.parse("2026-08-10T10:00:00Z");
    private static final Instant SECOND_TICK = Instant.parse("2026-08-10T10:05:00Z");

    private static final StorageOverview FIRST_OVERVIEW = new StorageOverview(
            new InfrastructureUsage(2_900_000_000L, 1_300_000_000L),
            List.of());

    private static final StorageOverview SECOND_OVERVIEW = new StorageOverview(
            new InfrastructureUsage(3_000_000_000L, 900_000_000L),
            List.of());

    @Mock
    HubStorageManager storageManager;

    @Mock
    Clock clock;

    StorageOverviewCache cache;

    @BeforeEach
    void setUp() {
        cache = new StorageOverviewCache(storageManager, clock);
    }

    @Nested
    class Get {

        @Test
        void computesOnce_whenNothingCachedYet() {
            when(storageManager.overview()).thenReturn(FIRST_OVERVIEW);
            when(clock.instant()).thenReturn(FIRST_TICK);

            CachedOverview cached = cache.get();

            assertThat(cached.overview()).isSameAs(FIRST_OVERVIEW);
            assertThat(cached.computedAt()).isEqualTo(FIRST_TICK);
            verify(storageManager, times(1)).overview();
        }

        @Test
        void returnsCachedSnapshot_withoutRecomputing() {
            when(storageManager.overview()).thenReturn(FIRST_OVERVIEW);
            when(clock.instant()).thenReturn(FIRST_TICK);

            CachedOverview first = cache.get();
            CachedOverview second = cache.get();

            assertThat(second).isSameAs(first);
            verify(storageManager, times(1)).overview();
        }
    }

    @Nested
    class Refresh {

        @Test
        void recomputesAndReplacesSnapshot() {
            when(storageManager.overview()).thenReturn(FIRST_OVERVIEW, SECOND_OVERVIEW);
            when(clock.instant()).thenReturn(FIRST_TICK, SECOND_TICK);

            CachedOverview first = cache.refresh();
            CachedOverview second = cache.refresh();

            assertThat(first.overview()).isSameAs(FIRST_OVERVIEW);
            assertThat(second.overview()).isSameAs(SECOND_OVERVIEW);
            assertThat(second.computedAt()).isEqualTo(SECOND_TICK);
            verify(storageManager, times(2)).overview();
        }

        @Test
        void refreshedSnapshotIsServedToReaders() {
            when(storageManager.overview()).thenReturn(FIRST_OVERVIEW, SECOND_OVERVIEW);
            when(clock.instant()).thenReturn(FIRST_TICK, SECOND_TICK);

            cache.get();
            CachedOverview refreshed = cache.refresh();

            assertThat(cache.get()).isSameAs(refreshed);
            verify(storageManager, times(2)).overview();
        }
    }
}
