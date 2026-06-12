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

import cafe.jeffrey.provider.profile.api.Event;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.Schedulers;
import cafe.jeffrey.shared.common.measure.Measuring;
import cafe.jeffrey.shared.persistence.DataSourceUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.node.ObjectNode;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Manual throughput sanity check for the Arrow events ingestion path. Pushes 1M synthetic
 * events (with realistic ~300B JSON {@code fields} documents) through {@link DuckDBArrowEventWriter}
 * from 4 caller threads — mirroring the per-parser-thread writer instances created by
 * {@code SQLEventWriter#newSingleThreadedWriter()} — into a single pooled file-backed
 * profile database, and reports the end-to-end events/s.
 *
 * <p>Disabled by default; run it manually with:
 * <pre>{@code
 * mvn test -pl jeffrey-microscope/profiles/profile-sql-persistence \
 *     -Dtest=DuckDBArrowEventWriterThroughputTest -Djeffrey.arrow.benchmark=true
 * }</pre>
 *
 * <p>Optional knobs: {@code -Djeffrey.arrow.benchmark.batchSize=25000} and
 * {@code -Djeffrey.arrow.benchmark.flushMode=shared|single} (the {@code single} mode reproduces
 * the old dedicated single-flush-thread configuration for before/after comparisons).
 */
@EnabledIfSystemProperty(named = "jeffrey.arrow.benchmark", matches = "true")
class DuckDBArrowEventWriterThroughputTest {

    private static final int CALLER_THREADS = 4;
    private static final int EVENTS_PER_THREAD = 250_000;

    private static final String BATCH_SIZE_PROPERTY = "jeffrey.arrow.benchmark.batchSize";
    private static final int DEFAULT_BATCH_SIZE = 25_000;

    private static final String FLUSH_MODE_PROPERTY = "jeffrey.arrow.benchmark.flushMode";
    private static final String FLUSH_MODE_SHARED = "shared";
    private static final String FLUSH_MODE_SINGLE = "single";

    private static final String BENCHMARK_PROFILE_ID = "arrow-throughput-benchmark";
    private static final String BENCHMARK_FLUSH_THREAD_PREFIX = "benchmark-arrow-flush";
    private static final String CALLER_THREAD_PREFIX = "benchmark-caller";

    private static final String COUNT_EVENTS_SQL = "SELECT COUNT(*) FROM events";

    private static final String SYNTHETIC_EVENT_TYPE = "jdk.ObjectAllocationSample";
    private static final Instant BASE_TIMESTAMP = Instant.parse("2026-01-15T10:00:00Z");

    @Test
    void fourCallerThreadsIngestOneMillionEvents(@TempDir Path tempDir) throws Exception {
        int batchSize = Integer.getInteger(BATCH_SIZE_PROPERTY, DEFAULT_BATCH_SIZE);
        String flushMode = System.getProperty(FLUSH_MODE_PROPERTY, FLUSH_MODE_SHARED);

        DataSource dataSource = new DuckDBProfileDatabaseManager(tempDir).open(BENCHMARK_PROFILE_ID);
        ExecutorService dedicatedFlushExecutor = FLUSH_MODE_SINGLE.equals(flushMode)
                ? Executors.newSingleThreadExecutor(Schedulers.platformThreadfactory(BENCHMARK_FLUSH_THREAD_PREFIX))
                : null;
        Executor flushExecutor = dedicatedFlushExecutor != null
                ? dedicatedFlushExecutor
                : Schedulers.sharedDbWriter();

        try {
            Duration elapsed = Measuring.r(() -> ingestFromCallerThreads(flushExecutor, dataSource, batchSize));

            long totalEvents = (long) CALLER_THREADS * EVENTS_PER_THREAD;
            assertEquals(totalEvents, countEvents(dataSource), "every synthetic event must be persisted");

            double eventsPerSecond = totalEvents / (elapsed.toNanos() / 1_000_000_000.0);
            // This test module has no SLF4J backend on the test classpath — print the result
            // to stdout so the manual benchmark run always shows it.
            System.out.printf("Arrow events ingestion throughput: events=%d caller_threads=%d batch_size=%d "
                            + "flush_mode=%s elapsed_ms=%d events_per_sec=%.0f%n",
                    totalEvents, CALLER_THREADS, batchSize, flushMode, elapsed.toMillis(), eventsPerSecond);
        } finally {
            if (dedicatedFlushExecutor != null) {
                dedicatedFlushExecutor.close();
            }
            DataSourceUtils.close(dataSource);
        }
    }

    private static void ingestFromCallerThreads(Executor flushExecutor, DataSource dataSource, int batchSize) {
        try (ExecutorService callers = Executors.newFixedThreadPool(
                CALLER_THREADS, Schedulers.platformThreadfactory(CALLER_THREAD_PREFIX))) {

            List<Future<?>> ingestions = new ArrayList<>();
            for (int thread = 0; thread < CALLER_THREADS; thread++) {
                int threadIndex = thread;
                ingestions.add(callers.submit(() -> ingestEvents(flushExecutor, dataSource, batchSize, threadIndex)));
            }
            for (Future<?> ingestion : ingestions) {
                ingestion.get();
            }
        } catch (Exception e) {
            throw new RuntimeException("Benchmark ingestion failed", e);
        }
    }

    /**
     * Mirrors a parser thread: a dedicated writer instance, events built and inserted on the
     * caller thread, writer closed when the thread's share of the recording is done.
     */
    private static void ingestEvents(Executor flushExecutor, DataSource dataSource, int batchSize, int threadIndex) {
        DuckDBArrowEventWriter writer = new DuckDBArrowEventWriter(flushExecutor, dataSource, batchSize, BASE_TIMESTAMP);
        for (int i = 0; i < EVENTS_PER_THREAD; i++) {
            writer.insert(syntheticEvent(threadIndex, i));
        }
        writer.close();
    }

    private static Event syntheticEvent(int threadIndex, int index) {
        ObjectNode fields = Json.createObject();
        fields.put("objectClass", "java.util.concurrent.ConcurrentHashMap$Node");
        fields.put("allocationSize", 4096L + index);
        fields.put("tlabSize", 65_536L);
        fields.put("eventThread", "benchmark-worker-" + (index % 64));
        fields.put("state", "RUNNABLE");
        fields.put("stackDepth", 64);
        fields.put("message", "synthetic allocation sample payload for the throughput benchmark run");

        return new Event(
                SYNTHETIC_EVENT_TYPE,
                BASE_TIMESTAMP.plusNanos((long) index * 1_000),
                250_000L,
                1L,
                4096L + index,
                "java.lang.String",
                (long) (index % 10_000),
                (long) threadIndex,
                fields);
    }

    private static long countEvents(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(COUNT_EVENTS_SQL)) {

            resultSet.next();
            return resultSet.getLong(1);
        }
    }
}
