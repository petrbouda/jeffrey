/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.provider.profile.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import pbouda.jeffrey.shared.common.Json;
import pbouda.jeffrey.shared.common.model.ThreadInfo;
import pbouda.jeffrey.shared.common.model.Type;
import pbouda.jeffrey.provider.profile.model.AllocatingThread;
import pbouda.jeffrey.shared.persistence.StatementLabel;
import pbouda.jeffrey.shared.persistence.client.DatabaseClient;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;
import pbouda.jeffrey.provider.profile.query.SQLFormatter;

import java.util.List;
import java.util.Optional;

import static pbouda.jeffrey.shared.persistence.GroupLabel.PROFILE_EVENTS;

public class JdbcProfileEventRepository implements ProfileEventRepository {

    //language=SQL
    private final String LATEST_JSON_QUERY = """
            SELECT events.fields::jsonb AS event_fields
            FROM events
            WHERE events.event_type = :event_type
            ORDER BY events.start_timestamp DESC LIMIT 1""";

    //language=SQL
    private final String ALLOCATING_THREADS_QUERY = """
            SELECT
                events.weight,
                threads.os_id,
                threads.java_id,
                threads.name
            FROM events
            LEFT JOIN threads ON events.thread_hash = threads.thread_hash
            WHERE events.event_type = 'jdk.ThreadAllocationStatistics'
                AND events.start_timestamp = (
                    SELECT MAX(start_timestamp) FROM events
                    WHERE event_type = 'jdk.ThreadAllocationStatistics'
                )
            ORDER BY events.weight DESC LIMIT :limit
            """;

    //language=SQL
    private static final String FIELDS_BY_EVENT = """
            SELECT events.event_type, events.fields::jsonb as event_fields FROM events
            WHERE events.event_type IN (:code)""";

    //language=SQL
    private static final String CONTAINS_EVENT =
            "SELECT COUNT(*) FROM events WHERE event_type = (:code)";

    private final SQLFormatter sqlFormatter;
    private final DatabaseClient databaseClient;

    public JdbcProfileEventRepository(
            SQLFormatter sqlFormatter,
            DatabaseClientProvider databaseClientProvider) {

        this.sqlFormatter = sqlFormatter;
        this.databaseClient = databaseClientProvider.provide(PROFILE_EVENTS);
    }

    @Override
    public Optional<ObjectNode> latestJsonFields(Type type) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("event_type", type.code());

        return databaseClient.querySingle(
                StatementLabel.FIND_LATEST_JSON,
                sqlFormatter.formatJson(LATEST_JSON_QUERY),
                params,
                (rs, _) -> (ObjectNode) Json.readTree(rs.getString("event_fields")));
    }

    @Override
    public List<AllocatingThread> allocatingThreads(int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", limit);

        return databaseClient.query(
                StatementLabel.ALLOCATING_THREADS,
                ALLOCATING_THREADS_QUERY,
                params,
                (rs, _) -> new AllocatingThread(
                        new ThreadInfo(
                                rs.getLong("os_id"),
                                rs.getLong("java_id"),
                                rs.getString("name")),
                        rs.getLong("weight")));
    }

    @Override
    public List<JsonNode> eventsByTypeWithFields(Type type) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("code", type.code());

        return databaseClient.query(
                StatementLabel.FIELDS_WITH_EVENT_TYPE,
                sqlFormatter.formatJson(FIELDS_BY_EVENT),
                paramSource,
                (rs, _) -> Json.readTree(rs.getString("event_fields")));
    }

    @Override
    public boolean containsEventType(Type type) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("code", type.code());

        return databaseClient.queryExists(StatementLabel.CONTAINS_EVENT, CONTAINS_EVENT, paramSource);
    }
}
