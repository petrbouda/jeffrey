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

import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.shared.common.model.SpanInterval;
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
import static org.junit.jupiter.api.Assertions.assertFalse;

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

    private static long totalSamples(DatabaseClient client, List<SpanInterval> spanIntervals) {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withSpanIntervals(spanIntervals);
        String sql = DuckDBFlamegraphQueries.of(EVENT_TYPE, "").simple(configurer);

        MapSqlParameterSource params = new MapSqlParameterSource();
        SpanIntervalParams.apply(params, spanIntervals);

        return client.query(StatementLabel.STREAM_EVENTS, sql, params, (rs, _) -> rs.getLong("total_samples"))
                .stream()
                .mapToLong(Long::longValue)
                .sum();
    }

    @Test
    void keepsOnlyPerSpanThreadAndWindowSamples(DataSource dataSource) throws SQLException {
        TestUtils.executeSql(dataSource, "sql/events/insert-span-flamegraph.sql");
        DatabaseClient client = new DatabaseClientProvider(dataSource).provide(GroupLabel.PROFILE_EVENTS);

        List<SpanInterval> spanIntervals = List.of(
                new SpanInterval(2001L, A_FROM, A_TO),
                new SpanInterval(2002L, B_FROM, B_TO));

        // Only thread 2001 inside span A and thread 2002 inside span B survive; the GC thread and the
        // out-of-window 2001 sample are filtered out.
        assertEquals(2, totalSamples(client, spanIntervals));
    }

    @Test
    void singleSpanScopesToThatThreadAndWindow(DataSource dataSource) throws SQLException {
        TestUtils.executeSql(dataSource, "sql/events/insert-span-flamegraph.sql");
        DatabaseClient client = new DatabaseClientProvider(dataSource).provide(GroupLabel.PROFILE_EVENTS);

        List<SpanInterval> spanIntervals = List.of(new SpanInterval(2001L, A_FROM, A_TO));

        // Span A only → just the one thread-2001 sample inside its window.
        assertEquals(1, totalSamples(client, spanIntervals));
    }

    @Test
    void disabledFilterCountsEverySample(DataSource dataSource) throws SQLException {
        TestUtils.executeSql(dataSource, "sql/events/insert-span-flamegraph.sql");
        DatabaseClient client = new DatabaseClientProvider(dataSource).provide(GroupLabel.PROFILE_EVENTS);

        // No span scope → the predicate is spliced out entirely and all four execution samples count.
        String sql = DuckDBFlamegraphQueries.of(EVENT_TYPE, "").simple(new EventQueryConfigurer());
        assertFalse(sql.contains("span_thread_hashes"));

        assertEquals(4, totalSamples(client, null));
    }
}
