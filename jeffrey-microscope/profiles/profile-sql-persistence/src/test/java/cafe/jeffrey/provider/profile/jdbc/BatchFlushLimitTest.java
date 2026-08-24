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

import cafe.jeffrey.shared.persistence.StatementLabel;
import org.duckdb.DuckDBConnection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BatchFlushLimitTest {

    private static final int BATCH_SIZE = 1;

    @Test
    @DisplayName("a limit needs at least one slot to hand out")
    void rejectsANonPositiveNumberOfSlots() {
        assertThrows(IllegalArgumentException.class, () -> BatchFlushLimit.ofSlots(0));
        assertThrows(IllegalArgumentException.class, () -> BatchFlushLimit.ofSlots(-1));
    }

    /**
     * The behaviour the bound exists for: with every slot taken by a batch the writer pool has not
     * finished, the thread producing events waits instead of queueing another one onto the heap.
     * <p>
     * Both inserts run on one thread because that is the writer's contract — each parsing thread
     * owns its own writers, and the batch it fills is a plain list guarded by nothing else.
     */
    @Test
    @DisplayName("a producer waits once every slot is taken")
    void producerBlocksWhenNoSlotIsFree() throws Exception {
        CountDownLatch releaseWriters = new CountDownLatch(1);
        ExecutorService writers = Executors.newFixedThreadPool(2);
        ExecutorService producer = Executors.newSingleThreadExecutor();

        try {
            BlockingWriter writer = new BlockingWriter(writers, BatchFlushLimit.ofSlots(1), releaseWriters);

            Future<?> parsing = producer.submit(() -> {
                writer.insert("first");
                writer.insert("second");
                writer.close();
            });

            assertTrue(writer.started.await(2, TimeUnit.SECONDS), "the first batch never reached the writer");

            // The first batch holds the only slot and is parked inside execute(). The second insert
            // is therefore waiting in acquire() and has not been handed to the pool.
            Thread.sleep(200);
            assertEquals(1, writer.entered.get());

            releaseWriters.countDown();
            parsing.get(5, TimeUnit.SECONDS);

            assertEquals(2, writer.entered.get());
            assertEquals(2, writer.executed.get());
        } finally {
            releaseWriters.countDown();
            writers.shutdownNow();
            producer.shutdownNow();
        }
    }

    /**
     * A failing batch still has to give its slot back, or an ingest that hits one bad batch stalls
     * for good instead of reporting the failure at close().
     */
    @Test
    @DisplayName("a failed batch returns its slot")
    void failureDoesNotLeakASlot() {
        ExecutorService writers = Executors.newSingleThreadExecutor();
        try {
            FailingWriter writer = new FailingWriter(writers, BatchFlushLimit.ofSlots(1));

            // More batches than slots: each one can only proceed if the previous gave its slot back
            // despite failing.
            writer.insert("one");
            writer.insert("two");
            writer.insert("three");

            assertThrows(IllegalStateException.class, writer::close);
        } finally {
            writers.shutdownNow();
        }
    }

    private static DataSource failingDataSource() {
        DataSource dataSource = mock(DataSource.class);
        try {
            when(dataSource.getConnection()).thenThrow(new IllegalStateException("no connection"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return dataSource;
    }

    private static DataSource duckDbDataSource() {
        DataSource dataSource = mock(DataSource.class);
        try {
            Connection connection = mock(DuckDBConnection.class);
            when(connection.unwrap(any())).thenReturn(connection);
            when(connection.isWrapperFor(any())).thenReturn(true);
            when(dataSource.getConnection()).thenReturn(connection);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return dataSource;
    }

    private static final class BlockingWriter extends DuckDBBatchingWriter<String> {

        private final CountDownLatch release;
        private final CountDownLatch started = new CountDownLatch(1);
        /** Batches that reached the writer pool, counted before parking. */
        private final AtomicInteger entered = new AtomicInteger();
        /** Batches that got all the way through. */
        private final AtomicInteger executed = new AtomicInteger();

        private BlockingWriter(ExecutorService executor, BatchFlushLimit limit, CountDownLatch release) {
            super(executor, "events_raw", duckDbDataSource(), BATCH_SIZE,
                    StatementLabel.INSERT_EVENTS, limit);
            this.release = release;
        }

        @Override
        protected void execute(DuckDBConnection connection, List<String> events) throws Exception {
            entered.incrementAndGet();
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            executed.incrementAndGet();
        }
    }

    private static final class FailingWriter extends DuckDBBatchingWriter<String> {

        private FailingWriter(ExecutorService executor, BatchFlushLimit limit) {
            super(executor, "events_raw", failingDataSource(), BATCH_SIZE,
                    StatementLabel.INSERT_EVENTS, limit);
        }

        @Override
        protected void execute(DuckDBConnection connection, List<String> events) {
            // Never reached — acquiring the connection fails first.
        }
    }
}
