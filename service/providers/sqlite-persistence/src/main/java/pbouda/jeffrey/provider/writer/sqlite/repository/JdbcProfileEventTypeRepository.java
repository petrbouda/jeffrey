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
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.model.EventSource;
import pbouda.jeffrey.common.model.EventSubtype;
import pbouda.jeffrey.common.model.EventSummary;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.provider.api.model.EventTypeWithFields;
import pbouda.jeffrey.provider.api.model.FieldDescription;
import pbouda.jeffrey.provider.api.repository.ProfileEventTypeRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class JdbcProfileEventTypeRepository implements ProfileEventTypeRepository {

    private static final TypeReference<List<String>> STRING_LIST =
            new TypeReference<List<String>>() {
            };

    private static final TypeReference<Map<String, String>> STRING_MAP =
            new TypeReference<Map<String, String>>() {
            };

    private static final TypeReference<List<FieldDescription>> FIELD_DESC =
            new TypeReference<List<FieldDescription>>() {
            };

    private static final RowMapper<EventTypeWithFields> TYPE_FIELDS_MAPPER = (rs, _) -> {
        String name = rs.getString("name");
        String label = rs.getString("label");
        String fields = rs.getString("fields");
        return new EventTypeWithFields(name, label, Json.readObjectNode(fields));
    };

    private static final RowMapper<EventSummary> EVENT_SUMMARY_MAPPER = (rs, _) -> {
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
                toNullableMap(rs.getString("extras")),
                toNullableMap(rs.getString("settings")));
    };

    private final String profileId;
    private final JdbcClient jdbcClient;

    //language=SQL
    private static final String FIELDS_BY_SINGLE_EVENT = """
            SELECT event_types.name, event_types.label, event_fields.fields, events.* FROM events
            INNER JOIN event_types ON events.event_type = event_types.name
            INNER JOIN event_fields ON
                events.profile_id = event_fields.profile_id AND events.event_id = event_fields.event_id
            WHERE events.profile_id = (:profile_id) AND events.event_type = (:code) LIMIT 1""";

    //language=SQL
    private static final String FIELDS_BY_EVENT = """
            SELECT events.event_type, event_fields.fields FROM events
            INNER JOIN event_fields ON
                events.profile_id = event_fields.profile_id AND events.event_id = event_fields.event_id
            WHERE events.profile_id = (:profile_id) AND events.event_type IN (:code)""";

    //language=SQL
    private static final String CONTAINS_EVENT =
            "SELECT COUNT(*) FROM events WHERE profile_id = (:profile_id) AND event_type = (:code)";

    //language=SQL
    private static final String COLUMNS_BY_SINGLE_EVENT =
            "SELECT columns FROM event_types WHERE profile_id = (:profile_id) AND name = (:code) LIMIT 1";

    //language=SQL
    private static final String EVENT_SUMMARIES =
            "SELECT * FROM event_types WHERE profile_id = (:profile_id) AND name IN (:codes)";

    //language=SQL
    private static final String EVENT_TYPES_BY_ID =
            "SELECT * FROM event_types WHERE profile_id = (:profile_id)";

    public JdbcProfileEventTypeRepository(String profileId, JdbcClient jdbcClient) {
        this.profileId = profileId;
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<EventTypeWithFields> singleFieldsByEventType(Type type) {
        return jdbcClient.sql(FIELDS_BY_SINGLE_EVENT)
                .param("profile_id", profileId)
                .param("code", type.code())
                .query(TYPE_FIELDS_MAPPER)
                .optional();
    }

    @Override
    public List<JsonNode> eventsByTypeWithFields(Type type) {
        return jdbcClient.sql(FIELDS_BY_EVENT)
                .param("profile_id", profileId)
                .param("code", type.code())
                .query((rs, _) -> Json.readTree(rs.getString("fields")))
                .list();
    }

    @Override
    public boolean containsEventType(Type type) {
        Optional<Integer> optional = jdbcClient.sql(CONTAINS_EVENT)
                .param("profile_id", profileId)
                .param("code", type.code())
                .query(Integer.class)
                .optional();

        return optional.map(c -> c > 0).orElse(false);
    }

    @Override
    public List<FieldDescription> eventColumns(Type type) {
        RowMapper<List<FieldDescription>> columns = (rs, _) -> {
            return Json.read(rs.getString("columns"), FIELD_DESC);
        };

        return jdbcClient.sql(COLUMNS_BY_SINGLE_EVENT)
                .param("profile_id", profileId)
                .param("code", type.code())
                .query(columns)
                .single();
    }

    @Override
    public List<EventSummary> eventSummaries(List<Type> types) {
        List<String> codes = types.stream()
                .map(Type::code)
                .toList();

        return jdbcClient.sql(EVENT_SUMMARIES)
                .param("profile_id", profileId)
                .param("codes", codes)
                .query(EVENT_SUMMARY_MAPPER)
                .list();
    }

    @Override
    public List<EventSummary> eventSummaries() {
        return jdbcClient.sql(EVENT_TYPES_BY_ID)
                .param("profile_id", profileId)
                .query(EVENT_SUMMARY_MAPPER)
                .list();
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
