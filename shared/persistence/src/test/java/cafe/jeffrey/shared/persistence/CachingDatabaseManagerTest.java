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

package cafe.jeffrey.shared.persistence;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cafe.jeffrey.test.MutableClock;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CachingDatabaseManagerTest {

    private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration CHECK_INTERVAL = Duration.ofMinutes(1);

    private static DataSource closeablePool() {
        return mock(DataSource.class, withSettings().extraInterfaces(AutoCloseable.class));
    }

    private CachingDatabaseManager manager(DatabaseManager delegate, MutableClock clock) {
        // A no-op scheduler so eviction only runs when the test calls evictExpired() directly.
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        return new CachingDatabaseManager(delegate, clock, IDLE_TIMEOUT, CHECK_INTERVAL, scheduler);
    }

    @Nested
    class MultiEntryCaching {

        @Mock
        private DatabaseManager delegate;

        @Test
        void openReturnsSameDataSourceForSameUri() {
            DataSource pool = closeablePool();
            when(delegate.open("profile-1")).thenReturn(pool);

            CachingDatabaseManager manager = manager(delegate, new MutableClock(Instant.now()));

            DataSource ds1 = manager.open("profile-1");
            DataSource ds2 = manager.open("profile-1");

            assertSame(ds1, ds2);
            verify(delegate, times(1)).open("profile-1");
        }

        @Test
        void openingADifferentUriDoesNotCloseThePreviousPool() throws Exception {
            DataSource pool1 = closeablePool();
            DataSource pool2 = closeablePool();
            when(delegate.open("profile-1")).thenReturn(pool1);
            when(delegate.open("profile-2")).thenReturn(pool2);

            CachingDatabaseManager manager = manager(delegate, new MutableClock(Instant.now()));

            DataSource ds1 = manager.open("profile-1");
            DataSource ds2 = manager.open("profile-2");

            assertNotSame(ds1, ds2);
            // The regression that caused "appender was closed": switching profiles must NOT
            // tear down the first profile's still-in-use pool.
            verify((AutoCloseable) pool1, never()).close();
            verify((AutoCloseable) pool2, never()).close();
            // Both pools remain cached and usable.
            assertSame(ds1, manager.open("profile-1"));
            assertSame(ds2, manager.open("profile-2"));
        }
    }

    @Nested
    class IdleEviction {

        @Mock
        private DatabaseManager delegate;

        @Test
        void evictsAndClosesIdleEntryAfterTimeout() throws Exception {
            MutableClock clock = new MutableClock(Instant.now());
            DataSource pool = closeablePool();
            when(delegate.open("profile-1")).thenReturn(pool);

            CachingDatabaseManager manager = manager(delegate, clock);

            manager.open("profile-1");
            clock.advance(IDLE_TIMEOUT.plusMinutes(1));
            manager.evictExpired();

            verify((AutoCloseable) pool, times(1)).close();
        }

        @Test
        void doesNotEvictEntryBeforeTimeout() throws Exception {
            MutableClock clock = new MutableClock(Instant.now());
            DataSource pool = closeablePool();
            when(delegate.open("profile-1")).thenReturn(pool);

            CachingDatabaseManager manager = manager(delegate, clock);

            manager.open("profile-1");
            clock.advance(IDLE_TIMEOUT.minusMinutes(1));
            manager.evictExpired();

            verify((AutoCloseable) pool, never()).close();
        }
    }

    @Nested
    class PinnedLeases {

        @Mock
        private DatabaseManager delegate;

        @Test
        void pinnedEntryIsNotEvictedEvenWhenIdlePastTimeout() throws Exception {
            MutableClock clock = new MutableClock(Instant.now());
            DataSource pool = closeablePool();
            when(delegate.open("profile-1")).thenReturn(pool);

            CachingDatabaseManager manager = manager(delegate, clock);

            try (DatabaseLease lease = manager.acquire("profile-1")) {
                assertNotNull(lease.dataSource());
                // Simulate a long-running initialization: idle far beyond the timeout while pinned.
                clock.advance(IDLE_TIMEOUT.multipliedBy(10));
                manager.evictExpired();

                verify((AutoCloseable) pool, never()).close();
            }
        }

        @Test
        void entryIsEvictableAfterLeaseIsReleased() throws Exception {
            MutableClock clock = new MutableClock(Instant.now());
            DataSource pool = closeablePool();
            when(delegate.open("profile-1")).thenReturn(pool);

            CachingDatabaseManager manager = manager(delegate, clock);

            try (DatabaseLease lease = manager.acquire("profile-1")) {
                assertSame(manager.open("profile-1"), lease.dataSource());
            }

            clock.advance(IDLE_TIMEOUT.plusMinutes(1));
            manager.evictExpired();

            verify((AutoCloseable) pool, times(1)).close();
        }

        @Test
        void leaseAndOpenShareTheSameCachedPool() {
            DataSource pool = closeablePool();
            when(delegate.open("profile-1")).thenReturn(pool);

            CachingDatabaseManager manager = manager(delegate, new MutableClock(Instant.now()));

            try (DatabaseLease lease = manager.acquire("profile-1")) {
                DataSource readHandle = manager.open("profile-1");
                assertSame(lease.dataSource(), readHandle);
            }
            verify(delegate, times(1)).open("profile-1");
        }
    }

    @Nested
    class PoolLifecycleOwnership {

        @Mock
        private DatabaseManager delegate;

        @Test
        void returnedDataSourceIsShieldedFromDefensiveClose() throws Exception {
            DataSource pool = closeablePool();
            when(delegate.open("profile-1")).thenReturn(pool);

            CachingDatabaseManager manager = manager(delegate, new MutableClock(Instant.now()));

            DataSource handle = manager.open("profile-1");
            DataSourceUtils.close(handle);

            assertFalse(handle instanceof AutoCloseable);
            verify((AutoCloseable) pool, never()).close();
            assertSame(handle, manager.open("profile-1"));
        }

        @Test
        void returnedDataSourceDelegatesConnections() throws Exception {
            DataSource pool = closeablePool();
            Connection connection = mock(Connection.class);
            when(delegate.open("profile-1")).thenReturn(pool);
            when(pool.getConnection()).thenReturn(connection);

            CachingDatabaseManager manager = manager(delegate, new MutableClock(Instant.now()));

            DataSource handle = manager.open("profile-1");

            assertSame(connection, handle.getConnection());
        }
    }

    @Nested
    class Delegation {

        @Mock
        private DatabaseManager delegate;

        @Test
        void runMigrationsDelegatesToUnderlyingManager() {
            DataSource pool = closeablePool();
            when(delegate.open("profile-1")).thenReturn(pool);

            CachingDatabaseManager manager = manager(delegate, new MutableClock(Instant.now()));

            DataSource ds = manager.open("profile-1");
            manager.runMigrations(ds);

            verify(delegate, times(1)).runMigrations(ds);
        }
    }
}
