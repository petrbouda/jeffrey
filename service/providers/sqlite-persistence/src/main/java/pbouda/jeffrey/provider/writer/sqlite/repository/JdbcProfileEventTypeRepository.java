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
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.model.RecordingEventSource;
import pbouda.jeffrey.common.model.EventSubtype;
import pbouda.jeffrey.common.model.EventSummary;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.provider.api.model.EventTypeWithFields;
import pbouda.jeffrey.provider.api.model.FieldDescription;
import pbouda.jeffrey.provider.api.repository.ProfileEventTypeRepository;
import pbouda.jeffrey.provider.writer.sqlite.GroupLabel;
import pbouda.jeffrey.provider.writer.sqlite.StatementLabel;
import pbouda.jeffrey.provider.writer.sqlite.client.DatabaseClient;

import javax.sql.DataSource;
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
        String fields = rs.getString("json(events.fields)");
        return new EventTypeWithFields(name, label, Json.readObjectNode(fields));
    };

    private static final RowMapper<EventSummary> EVENT_SUMMARY_MAPPER = (rs, _) -> {
        return new EventSummary(
                rs.getString("name"),
                rs.getString("label"),
                RecordingEventSource.byId(rs.getInt("source")),
                EventSubtype.resolve(rs.getString("subtype")),
                rs.getLong("samples"),
                rs.getLong("weight"),
                rs.getBoolean("has_stacktrace"),
                rs.getBoolean("calculated"),
                toNullableList(rs.getString("categories")),
                toNullableMap(rs.getString("extras")),
                toNullableMap(rs.getString("settings")));
    };

    //language=SQL
    private static final String FIELDS_BY_SINGLE_EVENT = """
            SELECT event_types.name, event_types.label, json(events.fields) FROM events
            INNER JOIN event_types ON events.event_type = event_types.name
            WHERE events.profile_id = (:profile_id) AND events.event_type = (:code) LIMIT 1""";

    //language=SQL
    private static final String COLUMNS_BY_SINGLE_EVENT =
            "SELECT columns FROM event_types WHERE profile_id = (:profile_id) AND name = (:code) LIMIT 1";

    //language=SQL
    private static final String EVENT_SUMMARIES =
            "SELECT * FROM event_types WHERE profile_id = (:profile_id) AND name IN (:codes)";

    //language=SQL
    private static final String EVENT_TYPES_BY_ID =
            "SELECT * FROM event_types WHERE profile_id = (:profile_id)";

    private final String profileId;
    private final DatabaseClient databaseClient;

    public JdbcProfileEventTypeRepository(String profileId, DataSource dataSource) {
        this.profileId = profileId;
        this.databaseClient = new DatabaseClient(dataSource, GroupLabel.PROFILE_EVENT_TYPES);
    }

    @Override
    public Optional<EventTypeWithFields> singleFieldsByEventType(Type type) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("profile_id", profileId)
                .addValue("code", type.code());

        return databaseClient.querySingle(
                StatementLabel.FIELDS_WITH_SINGLE_EVENT, FIELDS_BY_SINGLE_EVENT, paramSource, TYPE_FIELDS_MAPPER);
    }

    @Override
    public List<FieldDescription> eventColumns(Type type) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("profile_id", profileId)
                .addValue("code", type.code());

        RowMapper<List<FieldDescription>> columns = (rs, _) ->
                Json.read(rs.getString("columns"), FIELD_DESC);

        return databaseClient.querySingle(
                StatementLabel.COLUMNS_BY_SINGLE_EVENT, COLUMNS_BY_SINGLE_EVENT, paramSource, columns)
                .orElse(List.of());
    }

    @Override
    public List<EventSummary> eventSummaries(List<Type> types) {
        List<String> codes = types.stream()
                .map(Type::code)
                .toList();

        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("profile_id", profileId)
                .addValue("codes", codes);

        return databaseClient.query(
                StatementLabel.EVENT_SUMMARIES, EVENT_SUMMARIES, paramSource, EVENT_SUMMARY_MAPPER);
    }

    @Override
    public List<EventSummary> eventSummaries() {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("profile_id", profileId);

        return databaseClient.query(
                StatementLabel.FIND_EVENT_TYPE, EVENT_TYPES_BY_ID, paramSource, EVENT_SUMMARY_MAPPER);
    }

    private static Map<String, String> toNullableMap(String json) {
        return json == null ? null : Json.read(json, STRING_MAP);
    }

    private static List<String> toNullableList(String json) {
        return json == null ? null : Json.read(json, STRING_LIST);
    }
}
