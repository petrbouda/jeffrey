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
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the real flamegraph SIMPLE query (resolved from {@link DuckDBFlamegraphQueries}) against
 * DuckDB to prove the span-scope predicate keeps only the samples taken on a span's own thread within
 * its window. Running the actual query through Spring's named-parameter binding also validates that the
 * {@code [:span_*]} list params expand into DuckDB list literals correctly for more than one span.
 */
@DuckDBTest(migration = "classpath:db/migration/profile")
class DuckDBFlamegraphSpanFilterTest {

    private static final String EVENT_TYPE = "jdk.ExecutionSample";
    private static final long A_FROM = Instant.parse("2025-01-15T10:00:00.000Z").toEpochMilli();
    private static final long A_TO = Instant.parse("2025-01-15T10:00:00.300Z").toEpochMilli();
    private static final long B_FROM = Instant.parse("2025-01-15T10:00:02.000Z").toEpochMilli();
    private static final long B_TO = Instant.parse("2025-01-15T10:00:02.200Z").toEpochMilli();

    private static long totalSamples(DatabaseClient client, MapSqlParameterSource params) {
        String sql = DuckDBFlamegraphQueries.of(EVENT_TYPE, "").simple();
        return client.query(StatementLabel.STREAM_EVENTS, sql, params, (rs, _) -> rs.getLong("total_samples"))
                .stream()
                .mapToLong(Long::longValue)
                .sum();
    }

    private static MapSqlParameterSource baseParams() {
        return new MapSqlParameterSource()
                .addValue("from_time", null)
                .addValue("to_time", null)
                .addValue("stacktrace_types", null)
                .addValue("included_tags", null)
                .addValue("excluded_tags", null);
    }

    @Test
    void keepsOnlyPerSpanThreadAndWindowSamples(DataSource dataSource) throws SQLException {
        TestUtils.executeSql(dataSource, "sql/events/insert-span-flamegraph.sql");
        DatabaseClient client = new DatabaseClientProvider(dataSource).provide(GroupLabel.PROFILE_EVENTS);

        MapSqlParameterSource params = baseParams()
                .addValue("span_filter_enabled", true)
                .addValue("span_thread_hashes", List.of(2001L, 2002L))
                .addValue("span_from_ms", List.of(A_FROM, B_FROM))
                .addValue("span_to_ms", List.of(A_TO, B_TO));

        // Only thread 2001 inside span A and thread 2002 inside span B survive; the GC thread and the
        // out-of-window 2001 sample are filtered out.
        assertEquals(2, totalSamples(client, params));
    }

    @Test
    void singleSpanScopesToThatThreadAndWindow(DataSource dataSource) throws SQLException {
        TestUtils.executeSql(dataSource, "sql/events/insert-span-flamegraph.sql");
        DatabaseClient client = new DatabaseClientProvider(dataSource).provide(GroupLabel.PROFILE_EVENTS);

        MapSqlParameterSource params = baseParams()
                .addValue("span_filter_enabled", true)
                .addValue("span_thread_hashes", List.of(2001L))
                .addValue("span_from_ms", List.of(A_FROM))
                .addValue("span_to_ms", List.of(A_TO));

        // Span A only → just the one thread-2001 sample inside its window.
        assertEquals(1, totalSamples(client, params));
    }

    @Test
    void disabledFilterCountsEverySample(DataSource dataSource) throws SQLException {
        TestUtils.executeSql(dataSource, "sql/events/insert-span-flamegraph.sql");
        DatabaseClient client = new DatabaseClientProvider(dataSource).provide(GroupLabel.PROFILE_EVENTS);

        MapSqlParameterSource params = baseParams()
                .addValue("span_filter_enabled", false)
                .addValue("span_thread_hashes", null)
                .addValue("span_from_ms", null)
                .addValue("span_to_ms", null);

        // No span scope → all four execution samples are counted.
        assertEquals(4, totalSamples(client, params));
    }
}
