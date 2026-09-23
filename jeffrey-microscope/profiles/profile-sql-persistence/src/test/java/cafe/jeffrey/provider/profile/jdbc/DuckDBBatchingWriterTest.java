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

    /** Wide enough never to be the thing under test here; the bound has its own test. */
    private static final BatchFlushLimit FLUSH_LIMIT = BatchFlushLimit.ofSlots(64);

    private static final class TestWriter extends DuckDBBatchingWriter<String> {

        private TestWriter(DataSource dataSource) {
            super(DIRECT_EXECUTOR, "events_raw", dataSource, BATCH_SIZE, StatementLabel.INSERT_EVENTS, FLUSH_LIMIT);
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
