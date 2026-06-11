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

import cafe.jeffrey.provider.profile.api.DatabaseWriter;
import cafe.jeffrey.provider.profile.api.Event;
import cafe.jeffrey.shared.common.Json;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import tools.jackson.databind.node.ObjectNode;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;

/**
 * Manual benchmark comparing ARROW vs APPENDER ingestion throughput on synthetic events.
 * Not executed by the test suite — run the main method directly:
 *
 * <pre>
 * java --add-opens=java.base/java.nio=ALL-UNNAMED \
 *     -cp &lt;test-classpath&gt; cafe.jeffrey.provider.profile.jdbc.ArrowVsAppenderIngestionBenchmark
 * </pre>
 */
public final class ArrowVsAppenderIngestionBenchmark {

    private static final int EVENT_POOL_SIZE = 100_000;
    private static final int REPETITIONS = 10;
    private static final int WARMUP_REPETITIONS = 1;
    private static final int APPENDER_BATCH_SIZE = 10_000;
    private static final int ARROW_BATCH_SIZE = 100_000;
    private static final String PROFILE_MIGRATIONS_LOCATION = "classpath:db/migration/profile";
    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    private ArrowVsAppenderIngestionBenchmark() {
    }

    public static void main(String[] args) throws Exception {
        // Events are pre-generated outside the measured section so the benchmark isolates
        // writer throughput, not synthetic-event construction.
        List<Event> eventPool = syntheticEvents();

        Path tempDir = Files.createTempDirectory("jeffrey-ingestion-benchmark");
        try {
            runBenchmark("warmup-appender", tempDir.resolve("warmup-appender.db"), false, eventPool, WARMUP_REPETITIONS);
            runBenchmark("warmup-arrow", tempDir.resolve("warmup-arrow.db"), true, eventPool, WARMUP_REPETITIONS);

            double appenderRate = runBenchmark("APPENDER", tempDir.resolve("appender.db"), false, eventPool, REPETITIONS);
            double arrowRate = runBenchmark("ARROW", tempDir.resolve("arrow.db"), true, eventPool, REPETITIONS);

            System.out.printf(Locale.ROOT, "Speedup ARROW vs APPENDER: %.2fx%n", arrowRate / appenderRate);
        } finally {
            try (var paths = Files.walk(tempDir)) {
                paths.sorted((a, b) -> b.compareTo(a)).forEach(path -> path.toFile().delete());
            }
        }
    }

    private static double runBenchmark(
            String label, Path dbPath, boolean arrow, List<Event> eventPool, int repetitions) throws Exception {

        int rowCount = eventPool.size() * repetitions;
        try (Connection connection = DriverManager.getConnection("jdbc:duckdb:" + dbPath.toAbsolutePath())) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA wal_autocheckpoint = '1TB'");
            }
            SingleConnectionDataSource dataSource = new SingleConnectionDataSource(connection, true);
            Flyway.configure().dataSource(dataSource).locations(PROFILE_MIGRATIONS_LOCATION).load().migrate();

            DatabaseWriter<Event> writer = arrow
                    ? new DuckDBArrowEventWriter(DIRECT_EXECUTOR, dataSource, ARROW_BATCH_SIZE)
                    : new DuckDBEventWriter(DIRECT_EXECUTOR, dataSource, APPENDER_BATCH_SIZE);

            long start = System.nanoTime();
            for (int repetition = 0; repetition < repetitions; repetition++) {
                for (Event event : eventPool) {
                    writer.insert(event);
                }
            }
            writer.close();
            long elapsedNanos = System.nanoTime() - start;

            double rowsPerSecond = rowCount / (elapsedNanos / 1_000_000_000.0);
            long persistedRows = countRows(dataSource);
            if (persistedRows != rowCount) {
                throw new IllegalStateException(
                        "Row count mismatch: expected=" + rowCount + " actual=" + persistedRows);
            }
            System.out.printf(Locale.ROOT, "%-16s rows=%d elapsed_ms=%d rate=%.0f rows/s%n",
                    label, rowCount, elapsedNanos / 1_000_000, rowsPerSecond);
            dataSource.destroy();
            return rowsPerSecond;
        }
    }

    private static List<Event> syntheticEvents() {
        Instant base = Instant.parse("2026-01-15T10:00:00Z");
        List<Event> events = new ArrayList<>(EVENT_POOL_SIZE);
        for (int i = 0; i < EVENT_POOL_SIZE; i++) {
            events.add(syntheticEvent(base, i));
        }
        return events;
    }

    private static Event syntheticEvent(Instant base, int index) {
        ObjectNode fields = Json.createObject();
        fields.put("state", "RUNNABLE");
        fields.put("threadName", "worker-thread-" + (index % 64));
        fields.put("javaThreadId", (long) (index % 64));
        fields.put("osThreadId", (long) (index % 64) + 10_000L);
        fields.put("samplingPeriod", 19_999_853L);
        fields.put("description", "synthetic event payload to approximate a ~330B JSON fields column ");
        return new Event(
                "jdk.ExecutionSample",
                base.plusNanos(index * 1_000L),
                index % 7 == 0 ? null : 19_999_853L,
                1L,
                index % 5 == 0 ? null : 8192L,
                index % 3 == 0 ? null : "java.lang.String",
                (long) (index % 10_000),
                (long) (index % 64),
                fields);
    }

    private static long countRows(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT count(*) FROM events")) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }
}
