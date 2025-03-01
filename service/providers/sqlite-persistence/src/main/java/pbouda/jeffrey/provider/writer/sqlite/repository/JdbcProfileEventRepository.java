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

package pbouda.jeffrey.provider.writer.sqlite.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import pbouda.jeffrey.common.*;
import pbouda.jeffrey.jfrparser.api.record.SimpleRecord;
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;
import pbouda.jeffrey.provider.api.repository.EventTypeWithFields;
import pbouda.jeffrey.provider.api.repository.RecordQuery;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class JdbcProfileEventRepository implements ProfileEventRepository {

    private static final TypeReference<List<String>> STRING_LIST =
            new TypeReference<List<String>>() {
            };

    private static final TypeReference<Map<String, String>> STRING_MAP =
            new TypeReference<Map<String, String>>() {
            };

    private static final RowMapper<EventTypeWithFields> TYPE_FIELDS_MAPPER = (rs, rowNum) -> {
        String name = rs.getString("name");
        String label = rs.getString("label");
        String fields = rs.getString("fields");
        return new EventTypeWithFields(name, label, Json.readObjectNode(fields));
    };

    private static final RowMapper<EventSummary> EVENT_SUMMARY_MAPPER = (rs, rowNum) -> {
        return new EventSummary(
                rs.getString("name"),
                rs.getString("label"),
                EventSource.byId(rs.getInt("source")),
                EventSubtype.resolve(rs.getString("subtype")),
                rs.getLong("samples"),
                rs.getLong("weight"),
                rs.getBoolean("has_stacktrace"),
                rs.getBoolean("calculated"),
                toNullableList(rs.getString("categories")),
                toNullableMap(rs.getString("extras")));
    };

    private final String profileId;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    //language=SQL
    private static final String FIELDS_BY_SINGLE_EVENT = """
            SELECT event_types.name, event_types.label, events.fields, events.* FROM events
            INNER JOIN event_types ON events.event_name = event_types.name
            WHERE events.profile_id = (:profile_id) AND events.event_name = (:code) LIMIT 1
            """;

    //language=SQL
    private static final String FIELDS_BY_EVENT = """
            SELECT event_name, fields FROM events
            WHERE profile_id = (:profile_id) AND event_name IN (:code)
            """;

    //language=SQL
    private static final String FIELDS_FOR_ACTIVE_SETTINGS = """
            SELECT event_types.name, event_types.label, events.fields, events.*  FROM events
            INNER JOIN event_types ON events.fields->>'id' = event_types.type_id
            WHERE events.profile_id = (:profile_id) AND events.event_name = 'jdk.ActiveSetting'
            """;

    //language=SQL
    private static final String CONTAINS_EVENT = """
            SELECT COUNT(*) FROM events WHERE profile_id = (:profile_id) AND event_name = (:code)
            """;

    //language=SQL
    private static final String COLUMNS_BY_SINGLE_EVENT = """
            SELECT columns FROM event_types WHERE profile_id = (:profile_id) AND name = (:code) LIMIT 1
            """;

    //language=SQL
    private static final String EVENT_SUMMARIES = """
            SELECT * FROM event_types WHERE profile_id = (:profile_id) AND name IN (:codes)
            """;

    //language=SQL
    private static final String EVENT_TYPES = """
            SELECT * FROM event_types WHERE profile_id = (:profile_id)
            """;

    public JdbcProfileEventRepository(String profileId, JdbcTemplate jdbcTemplate) {
        this.profileId = profileId;
        this.jdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    @Override
    public Optional<EventTypeWithFields> singleFieldsByEventType(Type type) {
        List<EventTypeWithFields> results =
                jdbcTemplate.query(FIELDS_BY_SINGLE_EVENT, params().addValue("code", type.code()), TYPE_FIELDS_MAPPER);
        return results.stream().findFirst();
    }

    @Override
    public List<JsonNode> eventsByTypeWithFields(Type type) {
        return jdbcTemplate.query(
                FIELDS_BY_EVENT,
                params().addValue("code", type.code()),
                (rs, rowNum) -> Json.readTree(rs.getString("fields")));
    }

    @Override
    public List<EventTypeWithFields> activeSettings() {
        return jdbcTemplate.query(FIELDS_FOR_ACTIVE_SETTINGS, params(), TYPE_FIELDS_MAPPER);
    }

    @Override
    public boolean containsEventType(Type type) {
        Integer count = jdbcTemplate.queryForObject(
                CONTAINS_EVENT, params().addValue("code", type.code()), Integer.class);

        return count != null && count > 0;
    }

    @Override
    public Stream<SimpleRecord> streamRecords(RecordQuery recordQuery) {
        return jdbcTemplate
                .getJdbcTemplate()
                .queryForStream(recordQuery.query(), new SimpleRecordRowMapper(recordQuery));
    }

    @Override
    public JsonNode eventColumns(Type type) {
        RowMapper<JsonNode> columns = (rs, rowNum) -> Json.readTree(rs.getString("columns"));
        List<JsonNode> result = jdbcTemplate.query(
                COLUMNS_BY_SINGLE_EVENT, params().addValue("code", type.code()), columns);

        if (result.size() == 1) {
            return result.getFirst();
        } else {
            throw new IllegalStateException("Invalid number of rows returned: type=" + type + " rows=" + result.size());
        }
    }

    @Override
    public List<EventSummary> eventSummaries(List<Type> types) {
        List<String> codes = types.stream()
                .map(Type::code)
                .toList();

        return jdbcTemplate.query(EVENT_SUMMARIES, params().addValue("codes", codes), EVENT_SUMMARY_MAPPER);
    }

    @Override
    public List<EventSummary> eventSummaries() {
        return jdbcTemplate.query(EVENT_TYPES, params(), EVENT_SUMMARY_MAPPER);
    }

    private static Map<String, String> toNullableMap(String json) {
        return json == null ? null : Json.read(json, STRING_MAP);
    }

    private static List<String> toNullableList(String json) {
        return json == null ? null : Json.read(json, STRING_LIST);
    }

    private MapSqlParameterSource params() {
        return new MapSqlParameterSource("profile_id", profileId);
    }
}
