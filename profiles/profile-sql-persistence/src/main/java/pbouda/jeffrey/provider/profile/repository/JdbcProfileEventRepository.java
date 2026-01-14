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
import pbouda.jeffrey.shared.common.model.EventTypeName;
import pbouda.jeffrey.shared.common.model.ThreadInfo;
import pbouda.jeffrey.shared.common.model.Type;
import pbouda.jeffrey.provider.profile.model.AllocatingThread;
import pbouda.jeffrey.provider.profile.model.JvmFlag;
import pbouda.jeffrey.provider.profile.model.JvmFlagDetail;
import pbouda.jeffrey.shared.persistence.StatementLabel;
import pbouda.jeffrey.shared.persistence.client.DatabaseClient;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;
import pbouda.jeffrey.provider.profile.query.SQLFormatter;

import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    private static final Set<String> FLAG_EVENT_TYPES = Set.of(
            EventTypeName.BOOLEAN_FLAG,
            EventTypeName.INT_FLAG,
            EventTypeName.UNSIGNED_INT_FLAG,
            EventTypeName.STRING_FLAG
    );

    //language=SQL
    private static final String STRING_RELATED_FLAGS_QUERY = """
            SELECT
                last(event_type) as event_type,
                json_extract_string(fields, '$.name') as flag_name,
                last(json_extract_string(fields, '$.value')) as flag_value,
                last(json_extract_string(fields, '$.origin')) as origin
            FROM events
            WHERE event_type IN (:flag_event_types)
              AND json_extract_string(fields, '$.name') IN (:flag_names)
            GROUP BY flag_name
            """;

    private static final Set<String> STRING_RELATED_FLAG_NAMES = Set.of(
            "UseStringDeduplication",
            "StringDeduplicationAgeThreshold",
            "UseG1GC",
            "UseZGC",
            "UseShenandoahGC",
            "UseParallelGC",
            "UseSerialGC",
            "CompactStrings",
            "OptimizeStringConcat"
    );

    private static final Set<String> ALL_FLAG_EVENT_TYPES = Set.of(
            EventTypeName.BOOLEAN_FLAG,
            EventTypeName.INT_FLAG,
            EventTypeName.UNSIGNED_INT_FLAG,
            EventTypeName.LONG_FLAG,
            EventTypeName.STRING_FLAG
    );

    //language=SQL
    private static final String ALL_FLAGS_QUERY = """
            WITH flag_history AS (
                SELECT
                    event_type,
                    json_extract_string(fields, '$.name') as flag_name,
                    json_extract_string(fields, '$.value') as flag_value,
                    json_extract_string(fields, '$.origin') as origin,
                    start_timestamp,
                    ROW_NUMBER() OVER (PARTITION BY json_extract_string(fields, '$.name') ORDER BY start_timestamp DESC) as rn
                FROM events
                WHERE event_type IN (:flag_event_types)
            ),
            flag_values AS (
                SELECT
                    flag_name,
                    list(DISTINCT flag_value) as all_values,
                    count(DISTINCT flag_value) as value_count
                FROM flag_history
                GROUP BY flag_name
            )
            SELECT
                h.event_type,
                h.flag_name,
                h.flag_value as current_value,
                h.origin,
                v.all_values,
                v.value_count
            FROM flag_history h
            JOIN flag_values v ON h.flag_name = v.flag_name
            WHERE h.rn = 1
            ORDER BY h.origin, h.flag_name
            """;

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

    @Override
    public List<JvmFlag> getStringRelatedFlags() {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("flag_event_types", FLAG_EVENT_TYPES)
                .addValue("flag_names", STRING_RELATED_FLAG_NAMES);

        return databaseClient.query(
                StatementLabel.STRING_RELATED_FLAGS,
                STRING_RELATED_FLAGS_QUERY,
                paramSource,
                (rs, _) -> {
                    String eventType = rs.getString("event_type");
                    String flagType = extractFlagType(eventType);
                    return new JvmFlag(
                            rs.getString("flag_name"),
                            rs.getString("flag_value"),
                            flagType,
                            rs.getString("origin")
                    );
                });
    }

    @Override
    public List<JvmFlagDetail> getAllFlags() {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("flag_event_types", ALL_FLAG_EVENT_TYPES);

        return databaseClient.query(
                StatementLabel.ALL_FLAGS,
                ALL_FLAGS_QUERY,
                paramSource,
                (rs, _) -> {
                    String eventType = rs.getString("event_type");
                    String flagType = extractFlagType(eventType);
                    String currentValue = rs.getString("current_value");
                    int valueCount = rs.getInt("value_count");
                    boolean hasChanged = valueCount > 1;

                    // Get all values from DuckDB array and filter out the current one for previous values
                    List<String> previousValues = List.of();
                    if (hasChanged) {
                        java.sql.Array sqlArray = rs.getArray("all_values");
                        if (sqlArray != null) {
                            Object[] arrayValues = (Object[]) sqlArray.getArray();
                            previousValues = java.util.Arrays.stream(arrayValues)
                                    .map(Object::toString)
                                    .filter(v -> !v.equals(currentValue))
                                    .toList();
                        }
                    }

                    return new JvmFlagDetail(
                            rs.getString("flag_name"),
                            currentValue,
                            flagType,
                            rs.getString("origin"),
                            previousValues,
                            hasChanged,
                            null  // description is enriched later by FlagsManager
                    );
                });
    }

    private String extractFlagType(String eventType) {
        return switch (eventType) {
            case EventTypeName.BOOLEAN_FLAG -> "Boolean";
            case EventTypeName.INT_FLAG -> "Int";
            case EventTypeName.UNSIGNED_INT_FLAG -> "UnsignedInt";
            case EventTypeName.STRING_FLAG -> "String";
            case EventTypeName.LONG_FLAG -> "Long";
            default -> "Unknown";
        };
    }
}
