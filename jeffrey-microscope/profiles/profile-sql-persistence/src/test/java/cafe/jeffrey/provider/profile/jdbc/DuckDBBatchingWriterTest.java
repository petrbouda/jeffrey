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

package cafe.jeffrey.provider.profile.jdbc;

import org.duckdb.DuckDBConnection;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.shared.persistence.StatementLabel;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DuckDBBatchingWriterTest {

    private static final int BATCH_SIZE = 2;

    /** Runs submitted batches synchronously so the test stays deterministic. */
    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    private static final class FailingConnectionDataSource {

        private final AtomicInteger connectionRequests = new AtomicInteger();
        private final SQLException failure = new SQLException("simulated connection failure");

        private DataSource create() throws SQLException {
            DataSource dataSource = mock(DataSource.class);
            when(dataSource.getConnection()).thenAnswer(_ -> {
                connectionRequests.incrementAndGet();
                throw failure;
            });
            return dataSource;
        }
    }

    private static final class TestWriter extends DuckDBBatchingWriter<String> {

        private TestWriter(DataSource dataSource) {
            super(DIRECT_EXECUTOR, "events", dataSource, BATCH_SIZE, StatementLabel.INSERT_EVENTS);
        }

        @Override
        protected void execute(DuckDBConnection connection, List<String> events) {
            // Never reached in these tests — the connection acquisition fails first.
        }
    }

    @Nested
    class FailurePropagation {

        @Test
        void closeRethrowsFirstAsyncBatchFailure() throws SQLException {
            FailingConnectionDataSource failing = new FailingConnectionDataSource();
            TestWriter writer = new TestWriter(failing.create());

            writer.insert("a");
            writer.insert("b"); // batch full -> async insert runs and fails

            IllegalStateException thrown = assertThrows(IllegalStateException.class, writer::close);
            assertSame(failing.failure, thrown.getCause());
        }

        @Test
        void noFurtherBatchesAreSubmittedAfterFailure() throws SQLException {
            FailingConnectionDataSource failing = new FailingConnectionDataSource();
            TestWriter writer = new TestWriter(failing.create());

            writer.insert("a");
            writer.insert("b"); // first batch fails
            writer.insert("c");
            writer.insert("d"); // would be the second batch — must be skipped

            assertThrows(IllegalStateException.class, writer::close);
            assertEquals(1, failing.connectionRequests.get(),
                    "Only the first batch should reach the data source after a failure");
        }
    }

    @Nested
    class SuccessPath {

        @Test
        void closeWithoutAnyEventsDoesNotThrow() throws SQLException {
            DataSource dataSource = mock(DataSource.class);
            TestWriter writer = new TestWriter(dataSource);
            assertDoesNotThrow(writer::close);
        }

        @Test
        void closeFlushesRemainingEventsAndSucceeds() throws SQLException {
            DataSource dataSource = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            DuckDBConnection duckDBConnection = mock(DuckDBConnection.class);
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.isWrapperFor(DuckDBConnection.class)).thenReturn(true);
            when(connection.unwrap(DuckDBConnection.class)).thenReturn(duckDBConnection);

            TestWriter writer = new TestWriter(dataSource);
            writer.insert("a"); // below batch size — flushed on close

            assertDoesNotThrow(writer::close);
        }
    }
}
