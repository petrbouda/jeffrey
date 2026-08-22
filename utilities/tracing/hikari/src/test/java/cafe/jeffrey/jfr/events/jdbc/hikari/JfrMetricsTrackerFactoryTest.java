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

package cafe.jeffrey.jfr.events.jdbc.hikari;

import cafe.jeffrey.jfr.events.jdbc.pool.JdbcPoolStatisticsEvent;
import cafe.jeffrey.jfr.events.jdbc.pool.PooledJdbcConnectionAcquiredEvent;
import cafe.jeffrey.jfr.events.jdbc.pool.PooledJdbcConnectionBorrowedEvent;
import cafe.jeffrey.jfr.events.test.JfrRecordings;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jdk.jfr.consumer.RecordedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercised through a real HikariCP pool, because the whole module is a mapping of Hikari's own
 * callbacks — a test that invoked the tracker directly would assert only that the mapping code
 * compiles, not that Hikari ever calls it.
 */
class JfrMetricsTrackerFactoryTest {

    private static final String POOL_NAME = "orders-pool";
    private static final String JDBC_URL = "jdbc:duckdb:";

    /** Long enough for JFR to fire the one-second periodic gauge at least once. */
    private static final long GAUGE_WINDOW_MILLIS = 1_500;

    @Test
    @DisplayName("acquiring and returning a connection records the pool's own timings")
    void recordsConnectionTimings() throws IOException {
        List<RecordedEvent> events = JfrRecordings.all(
                List.of(PooledJdbcConnectionAcquiredEvent.NAME, PooledJdbcConnectionBorrowedEvent.NAME),
                () -> withPool(pool -> {
                    try (Connection connection = pool.getConnection();
                         Statement statement = connection.createStatement()) {
                        statement.execute("SELECT 1");
                    }
                }));

        assertFalse(events.isEmpty(), "Hikari reports every acquire and every return");
        for (RecordedEvent event : events) {
            assertEquals(POOL_NAME, event.getString("poolName"));
            assertTrue(event.getLong("elapsedTime") >= 0, "a timespan is recorded in nanoseconds");
        }
    }

    @Test
    @DisplayName("the pool's live gauges are sampled periodically by JFR itself")
    void samplesTheGaugePeriodically() throws IOException {
        List<RecordedEvent> events = JfrRecordings.all(JdbcPoolStatisticsEvent.NAME, () ->
                withPool(pool -> {
                    try (Connection connection = pool.getConnection()) {
                        connection.createStatement().close();
                        // The gauge is JFR's to schedule; the pool is simply held open across it.
                        Thread.sleep(GAUGE_WINDOW_MILLIS);
                    }
                }));

        assertFalse(events.isEmpty(), "the periodic event should have fired at least once");
        RecordedEvent gauge = events.getFirst();
        assertEquals(POOL_NAME, gauge.getString("poolName"));
        assertTrue(gauge.getInt("total") >= 1, "a connection was in use while the gauge fired");
        assertTrue(gauge.getInt("max") >= gauge.getInt("total"));
    }

    @Test
    @DisplayName("closing the pool stops the sampler, so a dead pool is not read forever")
    void closingThePoolStopsTheSampler() throws IOException {
        withPool(pool -> {
            try (Connection connection = pool.getConnection()) {
                connection.createStatement().close();
            }
        });

        // The pool above is closed by withPool; nothing should be sampled after it.
        List<RecordedEvent> afterClose = JfrRecordings.all(JdbcPoolStatisticsEvent.NAME,
                () -> sleep(GAUGE_WINDOW_MILLIS));

        assertTrue(afterClose.isEmpty(), "the periodic sampler was removed when the pool shut down");
    }

    private interface PoolBody {
        void accept(HikariDataSource pool) throws Exception;
    }

    private static void withPool(PoolBody body) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(JDBC_URL);
        config.setPoolName(POOL_NAME);
        config.setMaximumPoolSize(2);
        config.setMetricsTrackerFactory(new JfrMetricsTrackerFactory());

        try (HikariDataSource pool = new HikariDataSource(config)) {
            body.accept(pool);
        } catch (SQLException | RuntimeException e) {
            throw new IllegalStateException(e);
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
