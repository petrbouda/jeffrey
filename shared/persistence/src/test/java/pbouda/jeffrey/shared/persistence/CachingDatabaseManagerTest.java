/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.shared.persistence;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pbouda.jeffrey.test.MutableClock;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CachingDatabaseManagerTest {

    @Nested
    class Caching {

        @Mock
        private DatabaseManager delegate;

        @Test
        void openReturnsSameDataSourceForSameUri() {
            Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
            DataSource mockDataSource = mock(DataSource.class);
            when(delegate.open("test-uri")).thenReturn(mockDataSource);

            CachingDatabaseManager manager = new CachingDatabaseManager(delegate, clock);

            DataSource ds1 = manager.open("test-uri");
            DataSource ds2 = manager.open("test-uri");

            assertSame(ds1, ds2);
            verify(delegate, times(1)).open("test-uri");
        }

        @Test
        void openReturnsDifferentDataSourceForDifferentUri() {
            Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
            DataSource mockDataSource1 = mock(DataSource.class);
            DataSource mockDataSource2 = mock(DataSource.class);
            when(delegate.open("uri-1")).thenReturn(mockDataSource1);
            when(delegate.open("uri-2")).thenReturn(mockDataSource2);

            CachingDatabaseManager manager = new CachingDatabaseManager(delegate, clock);

            DataSource ds1 = manager.open("uri-1");
            DataSource ds2 = manager.open("uri-2");

            assertNotSame(ds1, ds2);
            verify(delegate, times(1)).open("uri-1");
            verify(delegate, times(1)).open("uri-2");
        }
    }

    @Nested
    class Eviction {

        private static final Duration TEST_CHECK_INTERVAL = Duration.ofMillis(5);

        @Mock
        private DatabaseManager delegate;

        @Test
        void evictsEntryAfterExpiration() throws InterruptedException {
            MutableClock clock = new MutableClock(Instant.now());
            Duration expiration = Duration.ofSeconds(5);

            DataSource mockDataSource1 = mock(DataSource.class);
            DataSource mockDataSource2 = mock(DataSource.class);
            when(delegate.open("test-uri"))
                    .thenReturn(mockDataSource1)
                    .thenReturn(mockDataSource2);

            CachingDatabaseManager manager = new CachingDatabaseManager(delegate, clock, expiration, TEST_CHECK_INTERVAL);

            DataSource ds1 = manager.open("test-uri");
            verify(delegate, times(1)).open("test-uri");

            // Advance clock past expiration
            clock.advance(Duration.ofSeconds(10));

            // Wait for eviction cycle (runs every 5ms in tests)
            Thread.sleep(50);

            // Entry should be evicted, so next open creates new one
            DataSource ds2 = manager.open("test-uri");

            assertNotSame(ds1, ds2);
            verify(delegate, times(2)).open("test-uri");
        }

        @Test
        void doesNotEvictEntryBeforeExpiration() throws InterruptedException {
            MutableClock clock = new MutableClock(Instant.now());
            Duration expiration = Duration.ofSeconds(10);

            DataSource mockDataSource = mock(DataSource.class);
            when(delegate.open("test-uri")).thenReturn(mockDataSource);

            CachingDatabaseManager manager = new CachingDatabaseManager(delegate, clock, expiration, TEST_CHECK_INTERVAL);

            DataSource ds1 = manager.open("test-uri");
            verify(delegate, times(1)).open("test-uri");

            // Advance clock but not past expiration
            clock.advance(Duration.ofSeconds(5));

            // Wait for eviction cycle (runs every 5ms in tests)
            Thread.sleep(50);

            // Entry should NOT be evicted
            DataSource ds2 = manager.open("test-uri");

            assertSame(ds1, ds2);
            verify(delegate, times(1)).open("test-uri");
        }
    }

    @Nested
    class Delegation {

        @Mock
        private DatabaseManager delegate;

        @Test
        void runMigrationsDelegatesToUnderlyingManager() {
            Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
            DataSource mockDataSource = mock(DataSource.class);
            when(delegate.open("test-uri")).thenReturn(mockDataSource);

            CachingDatabaseManager manager = new CachingDatabaseManager(delegate, clock);

            DataSource ds = manager.open("test-uri");
            manager.runMigrations(ds);

            verify(delegate, times(1)).runMigrations(ds);
        }
    }
}
