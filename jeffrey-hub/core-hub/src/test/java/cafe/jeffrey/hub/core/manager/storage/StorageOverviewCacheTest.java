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

package cafe.jeffrey.hub.core.manager.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cafe.jeffrey.hub.core.manager.storage.StorageOverview.DiskSpace;
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
            new DiskSpace(512_000_000_000L, 387_000_000_000L),
            new InfrastructureUsage(2_900_000_000L, 214_000_000L, 1_300_000_000L),
            List.of());

    private static final StorageOverview SECOND_OVERVIEW = new StorageOverview(
            new DiskSpace(512_000_000_000L, 350_000_000_000L),
            new InfrastructureUsage(3_000_000_000L, 220_000_000L, 900_000_000L),
            List.of());

    @Mock
    StorageManager storageManager;

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
