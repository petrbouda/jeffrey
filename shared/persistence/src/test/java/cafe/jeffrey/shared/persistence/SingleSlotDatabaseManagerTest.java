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

import javax.sql.DataSource;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SingleSlotDatabaseManagerTest {

    private static DataSource closeablePool() {
        return mock(DataSource.class, withSettings().extraInterfaces(AutoCloseable.class));
    }

    @Nested
    class SingleSlot {

        @Mock
        private DatabaseManager delegate;

        @Test
        void openReturnsSameDataSourceForSameUri() {
            DataSource pool = closeablePool();
            when(delegate.open("profile-1")).thenReturn(pool);

            SingleSlotDatabaseManager manager = new SingleSlotDatabaseManager(delegate);

            DataSource ds1 = manager.open("profile-1");
            DataSource ds2 = manager.open("profile-1");

            assertSame(ds1, ds2);
            verify(delegate, times(1)).open("profile-1");
        }

        @Test
        void openClosesPreviousPoolWhenSwitchingToDifferentUri() throws Exception {
            DataSource pool1 = closeablePool();
            DataSource pool2 = closeablePool();
            when(delegate.open("profile-1")).thenReturn(pool1);
            when(delegate.open("profile-2")).thenReturn(pool2);

            SingleSlotDatabaseManager manager = new SingleSlotDatabaseManager(delegate);

            DataSource ds1 = manager.open("profile-1");
            DataSource ds2 = manager.open("profile-2");

            assertNotSame(ds1, ds2);
            verify((AutoCloseable) pool1, times(1)).close();
            verify((AutoCloseable) pool2, never()).close();
        }

        @Test
        void reopeningPreviouslyClosedUriCreatesFreshPool() {
            DataSource pool1 = closeablePool();
            DataSource pool2 = closeablePool();
            DataSource pool3 = closeablePool();
            when(delegate.open("profile-1")).thenReturn(pool1, pool3);
            when(delegate.open("profile-2")).thenReturn(pool2);

            SingleSlotDatabaseManager manager = new SingleSlotDatabaseManager(delegate);

            DataSource first = manager.open("profile-1");
            manager.open("profile-2");
            DataSource reopened = manager.open("profile-1");

            assertNotSame(first, reopened);
            verify(delegate, times(2)).open("profile-1");
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

            SingleSlotDatabaseManager manager = new SingleSlotDatabaseManager(delegate);

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

            SingleSlotDatabaseManager manager = new SingleSlotDatabaseManager(delegate);

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

            SingleSlotDatabaseManager manager = new SingleSlotDatabaseManager(delegate);

            DataSource ds = manager.open("profile-1");
            manager.runMigrations(ds);

            verify(delegate, times(1)).runMigrations(ds);
        }
    }
}
