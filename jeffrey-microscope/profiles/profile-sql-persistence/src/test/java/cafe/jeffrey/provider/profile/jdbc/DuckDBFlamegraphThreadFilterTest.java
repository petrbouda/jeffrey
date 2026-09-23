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

import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.microscope.model.ThreadInfo;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;
import cafe.jeffrey.test.TestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Runs the real flamegraph SIMPLE query against DuckDB to prove that a graph opened on a collapsed
 * timeline lane covers every thread in the group and nothing else.
 *
 * <p>Going through Spring's named-parameter binding also proves the {@code [:*_thread_ids]} params
 * expand into DuckDB list literals — the filter used to take a single thread, so a list is new.
 */
@DuckDBTest(migration = "classpath:db/migration/profile")
class DuckDBFlamegraphThreadFilterTest {

    private static final String EVENT_TYPE = "jdk.ExecutionSample";

    /** Two pool workers with a Java id, and one that never got one and reports {@code -1}. */
    private static final List<ThreadInfo> POOL = List.of(
            new ThreadInfo(41, 12, "oracleApp:connection-adder"),
            new ThreadInfo(42, 13, "oracleApp:connection-adder"),
            new ThreadInfo(43, -1, "oracleApp:connection-adder"));

    /**
     * Runs {@code byThreadAndWeight} — the query the flamegraph actually resolves to once a thread
     * filter is set, and the only family that joins {@code threads} and carries the predicate.
     */
    private static long totalSamples(DatabaseClient client, List<ThreadInfo> threads) {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withThreads()
                .withSpecifiedThreads(threads);
        String sql = DuckDBFlamegraphQueries.of(EVENT_TYPE, "").byThreadAndWeight(configurer);

        MapSqlParameterSource params = new MapSqlParameterSource();
        ThreadIdParams.apply(params, configurer.specifiedThreads());

        return client.query(StatementLabel.STREAM_EVENTS, sql, params, (rs, _) -> rs.getLong("total_samples"))
                .stream()
                .mapToLong(Long::longValue)
                .sum();
    }

    @Test
    void coversEveryThreadInAGroup(DataSource dataSource) throws SQLException {
        TestUtils.executeSql(dataSource, "sql/events/insert-thread-group-flamegraph.sql");
        DatabaseClient client = new DatabaseClientProvider(dataSource).provide(GroupLabel.PROFILE_EVENTS);

        assertEquals(3, totalSamples(client, POOL),
                "All three pool workers contribute, including the one matched on its OS id");
    }

    @Test
    void keepsASingleThreadToItself(DataSource dataSource) throws SQLException {
        TestUtils.executeSql(dataSource, "sql/events/insert-thread-group-flamegraph.sql");
        DatabaseClient client = new DatabaseClientProvider(dataSource).provide(GroupLabel.PROFILE_EVENTS);

        assertEquals(1, totalSamples(client, List.of(POOL.getFirst())),
                "One thread is still one thread — the list did not widen the single-thread case");
    }

    /**
     * A thread with no Java id reports {@code -1}, and so does every other such thread. Matching on
     * that marker rather than on the OS id would sweep in threads that are not in the group.
     */
    @Test
    void doesNotSweepInOtherThreadsWithoutAJavaId(DataSource dataSource) throws SQLException {
        TestUtils.executeSql(dataSource, "sql/events/insert-thread-group-flamegraph.sql");
        DatabaseClient client = new DatabaseClientProvider(dataSource).provide(GroupLabel.PROFILE_EVENTS);

        assertEquals(1, totalSamples(client, List.of(new ThreadInfo(43, -1, "oracleApp:connection-adder"))),
                "Only the worker with OS id 43 counts, not the GC thread that also reports -1");
    }

    @Test
    void leavesTheGraphUnfilteredWithoutThreads(DataSource dataSource) throws SQLException {
        TestUtils.executeSql(dataSource, "sql/events/insert-thread-group-flamegraph.sql");
        DatabaseClient client = new DatabaseClientProvider(dataSource).provide(GroupLabel.PROFILE_EVENTS);

        assertEquals(5, totalSamples(client, List.of()), "No thread filter means the whole recording");
    }
}
