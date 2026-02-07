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

import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import pbouda.jeffrey.shared.common.Json;
import pbouda.jeffrey.shared.common.model.RecordingEventSource;
import pbouda.jeffrey.shared.common.model.EventSubtype;
import pbouda.jeffrey.shared.common.model.EventSummary;
import pbouda.jeffrey.shared.common.model.Type;
import pbouda.jeffrey.provider.profile.model.EventTypeWithFields;
import pbouda.jeffrey.provider.profile.model.FieldDescription;
import pbouda.jeffrey.shared.persistence.GroupLabel;
import pbouda.jeffrey.shared.persistence.StatementLabel;
import pbouda.jeffrey.provider.profile.calculated.EventSummaryCalculator;
import pbouda.jeffrey.provider.profile.calculated.NativeLeakEventSummaryCalculator;
import pbouda.jeffrey.shared.persistence.client.DatabaseClient;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;
import pbouda.jeffrey.provider.profile.query.SQLFormatter;

import java.util.ArrayList;
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

    private static final RowMapper<EventTypeWithFields> TYPE_FIELDS_MAPPER = (rs, __) -> {
        String name = rs.getString("name");
        String label = rs.getString("label");
        String fields = rs.getString("event_fields");
        return new EventTypeWithFields(name, label, Json.readObjectNode(fields));
    };

    private static final RowMapper<EventSummary> EVENT_SUMMARY_MAPPER = (rs, __) -> {
        return new EventSummary(
                rs.getString("name"),
                rs.getString("label"),
                RecordingEventSource.byId(rs.getInt("source")),
                EventSubtype.resolve(rs.getString("subtype")),
                rs.getLong("samples"),
                rs.getLong("weight"),
                rs.getBoolean("has_stacktrace"),
                false,
                toNullableList(rs.getString("categories")),
                toNullableMap(rs.getString("extras")),
                toNullableMap(rs.getString("settings")));
    };

    //language=SQL
    private static final String FIELDS_BY_SINGLE_EVENT = """
            SELECT event_types.name, event_types.label, events.fields::JSON AS event_fields FROM events
            INNER JOIN event_types ON events.event_type = event_types.name
            WHERE events.event_type = (:code) LIMIT 1""";

    //language=SQL
    private static final String COLUMNS_BY_SINGLE_EVENT =
            "SELECT columns FROM event_types WHERE name = (:code) LIMIT 1";

    //language=SQL
    private static final String EVENT_SUMMARIES_BY_CODES = """
            WITH aggregated_events AS (
                SELECT
                    event_type,
                    SUM(samples) as samples,
                    SUM(weight) as weight
                FROM events
                WHERE event_type IN (:codes)
                GROUP BY event_type
            )
            SELECT
                et.name,
                et.label,
                et.source,
                et.subtype,
                COALESCE(ae.samples, 0) as samples,
                COALESCE(ae.weight, 0) as weight,
                et.has_stacktrace,
                et.categories,
                et.extras,
                et.settings
            FROM event_types et
            LEFT JOIN aggregated_events ae ON et.name = ae.event_type
            WHERE et.name IN (:codes)""";

    //language=SQL
    private static final String EVENT_SUMMARIES = """
            WITH aggregated_events AS (
                SELECT
                    event_type,
                    SUM(samples) as samples,
                    SUM(weight) as weight
                FROM events
                GROUP BY event_type
            )
            SELECT
                et.name,
                et.label,
                et.source,
                et.subtype,
                COALESCE(ae.samples, 0) as samples,
                COALESCE(ae.weight, 0) as weight,
                et.has_stacktrace,
                et.categories,
                et.extras,
                et.settings
            FROM event_types et
            LEFT JOIN aggregated_events ae ON et.name = ae.event_type""";

    private final SQLFormatter sqlFormatter;
    private final DatabaseClient databaseClient;
    private final List<EventSummaryCalculator> commonCalculators;

    public JdbcProfileEventTypeRepository(
            SQLFormatter sqlFormatter, DatabaseClientProvider databaseClientProvider) {

        this.sqlFormatter = sqlFormatter;
        this.databaseClient = databaseClientProvider.provide(GroupLabel.PROFILE_EVENT_TYPES);
        this.commonCalculators = List.of(new NativeLeakEventSummaryCalculator(databaseClientProvider));
    }

    @Override
    public Optional<EventTypeWithFields> singleFieldsByEventType(Type type) {
        /*
         * Calculated event types are not included, it's not need at the time of the implementation.
         */
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("code", type.code());

        return databaseClient.querySingle(
                StatementLabel.FIELDS_WITH_SINGLE_EVENT,
                sqlFormatter.formatJson(FIELDS_BY_SINGLE_EVENT),
                paramSource,
                TYPE_FIELDS_MAPPER);
    }

    @Override
    public List<FieldDescription> eventColumns(Type type) {
        Optional<List<FieldDescription>> calculatedEventColumnsOpt = findCalculatedEventColumns(type);
        // Return calculated columns if present
        if (calculatedEventColumnsOpt.isPresent()) {
            return calculatedEventColumnsOpt.get();
        }

        // Fallback to database stored columns
        Optional<List<FieldDescription>> databaseEventColumnsOpt = findDatabaseEventColumns(type);
        return databaseEventColumnsOpt.orElse(List.of());
    }

    private Optional<List<FieldDescription>> findCalculatedEventColumns(Type type) {
        return commonCalculators.stream()
                .filter(calculator ->  type.equals(calculator.type()))
                .filter(EventSummaryCalculator::applicable)
                .map(EventSummaryCalculator::fieldDescriptions)
                .findAny();
    }

    private Optional<List<FieldDescription>> findDatabaseEventColumns(Type type) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("code", type.code());

        RowMapper<List<FieldDescription>> columns = (rs, __) ->
                Json.read(rs.getString("columns"), FIELD_DESC);

        return databaseClient.querySingle(
                        StatementLabel.COLUMNS_BY_SINGLE_EVENT, COLUMNS_BY_SINGLE_EVENT, paramSource, columns);
    }

    @Override
    public List<EventSummary> eventSummaries(List<Type> types) {
        List<String> codes = types.stream()
                .map(Type::code)
                .toList();

        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("codes", codes);

        List<EventSummary> eventSummaries = databaseClient.query(
                StatementLabel.EVENT_SUMMARIES, EVENT_SUMMARIES_BY_CODES, paramSource, EVENT_SUMMARY_MAPPER);

        List<EventSummary> calculatedEventSummaries = commonCalculators.stream()
                .filter(calculator -> types.contains(calculator.type()))
                .filter(EventSummaryCalculator::applicable)
                .map(EventSummaryCalculator::eventSummary)
                .toList();

        return combine(eventSummaries, calculatedEventSummaries);
    }

    @Override
    public List<EventSummary> eventSummaries() {
        List<EventSummary> eventSummaries = databaseClient.query(
                StatementLabel.FIND_EVENT_TYPE, EVENT_SUMMARIES, new MapSqlParameterSource(), EVENT_SUMMARY_MAPPER);

        List<EventSummary> calculatedEventSummaries = commonCalculators.stream()
                .filter(EventSummaryCalculator::applicable)
                .map(EventSummaryCalculator::eventSummary)
                .toList();

        return combine(eventSummaries, calculatedEventSummaries);
    }

    private static List<EventSummary> combine(List<EventSummary> database, List<EventSummary> calculated) {
        List<EventSummary> combined = new ArrayList<>();
        combined.addAll(database);
        combined.addAll(calculated);
        return combined;
    }

    private static Map<String, String> toNullableMap(String json) {
        return json == null ? null : Json.read(json, STRING_MAP);
    }

    private static List<String> toNullableList(String json) {
        return json == null ? null : Json.read(json, STRING_LIST);
    }
}
