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
import cafe.jeffrey.shared.common.model.JsonFieldFilter;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Exercises the real flamegraph SIMPLE query (resolved from {@link DuckDBFlamegraphQueries}) against
 * DuckDB to prove the JSON-field equality predicate keeps only the samples linked to the requested
 * OpenTelemetry trace/span (persisted in the {@code events.fields} JSON column by the OTLP parser).
 */
@DuckDBTest(migration = "classpath:db/migration/profile")
class DuckDBFlamegraphJsonFieldFilterTest {

    private static final String EVENT_TYPE = "otel.cpu";
    private static final String TRACE_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String SPAN_OF_TRACE_B = "3333333333333333";
    private static final String UNKNOWN_TRACE = "cccccccccccccccccccccccccccccccc";

    private static final String JSON_ROOT_PATH_PREFIX = "$.";

    private static long totalSamples(DatabaseClient client, JsonFieldFilter filter) {
        EventQueryConfigurer configurer = new EventQueryConfigurer();
        if (filter != null) {
            configurer.withJsonFieldEquals(filter.field(), filter.value());
        }
        String sql = DuckDBFlamegraphQueries.of(EVENT_TYPE, "").simple(configurer);

        MapSqlParameterSource params = new MapSqlParameterSource();
        if (filter != null) {
            params.addValue("json_field_path", JSON_ROOT_PATH_PREFIX + filter.field());
            params.addValue("json_field_value", filter.value());
        }

        return client.query(StatementLabel.STREAM_EVENTS, sql, params, (rs, _) -> rs.getLong("total_samples"))
                .stream()
                .mapToLong(Long::longValue)
                .sum();
    }

    @Test
    void traceIdFilterKeepsOnlyLinkedSamples(DataSource dataSource) throws SQLException {
        TestUtils.executeSql(dataSource, "sql/events/insert-trace-flamegraph.sql");
        DatabaseClient client = new DatabaseClientProvider(dataSource).provide(GroupLabel.PROFILE_EVENTS);

        // Two of the four samples belong to trace A.
        assertEquals(2, totalSamples(client, JsonFieldFilter.byTraceId(TRACE_A)));
    }

    @Test
    void spanIdFilterKeepsOnlyThatSpan(DataSource dataSource) throws SQLException {
        TestUtils.executeSql(dataSource, "sql/events/insert-trace-flamegraph.sql");
        DatabaseClient client = new DatabaseClientProvider(dataSource).provide(GroupLabel.PROFILE_EVENTS);

        assertEquals(1, totalSamples(client, JsonFieldFilter.bySpanId(SPAN_OF_TRACE_B)));
    }

    @Test
    void unknownTraceMatchesNothing(DataSource dataSource) throws SQLException {
        TestUtils.executeSql(dataSource, "sql/events/insert-trace-flamegraph.sql");
        DatabaseClient client = new DatabaseClientProvider(dataSource).provide(GroupLabel.PROFILE_EVENTS);

        // Includes the sample without any trace link — json_extract_string returns NULL and never matches.
        assertEquals(0, totalSamples(client, JsonFieldFilter.byTraceId(UNKNOWN_TRACE)));
    }

    @Test
    void disabledFilterCountsEverySample(DataSource dataSource) throws SQLException {
        TestUtils.executeSql(dataSource, "sql/events/insert-trace-flamegraph.sql");
        DatabaseClient client = new DatabaseClientProvider(dataSource).provide(GroupLabel.PROFILE_EVENTS);

        // Without the filter the predicate is spliced out entirely and all four samples count.
        String sql = DuckDBFlamegraphQueries.of(EVENT_TYPE, "").simple(new EventQueryConfigurer());
        assertFalse(sql.contains("json_extract_string"));

        assertEquals(4, totalSamples(client, null));
    }
}
