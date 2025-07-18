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

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.provider.api.repository.EventQueryConfigurer;
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;
import pbouda.jeffrey.provider.api.streamer.EventStreamerFactory;
import pbouda.jeffrey.provider.api.streamer.model.GenericRecord;
import pbouda.jeffrey.provider.writer.sqlite.StatementLabel;
import pbouda.jeffrey.provider.writer.sqlite.client.DatabaseClient;
import pbouda.jeffrey.provider.writer.sqlite.query.GenericRecordRowMapper;
import pbouda.jeffrey.provider.writer.sqlite.query.JdbcEventStreamerFactory;
import pbouda.jeffrey.provider.writer.sqlite.query.builder.NativeLeakQueryBuilderFactory;
import pbouda.jeffrey.provider.writer.sqlite.query.builder.QueryBuilderFactory;
import pbouda.jeffrey.provider.writer.sqlite.query.builder.DefaultQueryBuilderFactory;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;

import static pbouda.jeffrey.provider.writer.sqlite.GroupLabel.PROFILE_EVENTS;

public class JdbcProfileEventRepository implements ProfileEventRepository {

    //language=SQL
    private final String SINGLE_LATEST_QUERY = """
            SELECT
                events.event_type,
                events.start_timestamp,
                events.start_timestamp_from_beginning,
                events.duration,
                events.samples,
                events.weight,
                events.weight_entity,
                json(events.fields)
                FROM events
            WHERE events.profile_id = :profile_id AND events.event_type = :event_type
            ORDER BY events.start_timestamp_from_beginning DESC LIMIT 1""";

    //language=SQL
    private final String ALL_LATEST_QUERY = """
            SELECT
                events.event_type,
                events.start_timestamp,
                events.start_timestamp_from_beginning,
                events.duration,
                events.samples,
                events.weight,
                events.weight_entity,
                threads.os_id,
                threads.java_id,
                threads.name,
                threads.is_virtual
                FROM events
                LEFT JOIN threads ON (threads.profile_id = :profile_id AND events.thread_id = threads.thread_id)
            WHERE events.profile_id = :profile_id AND events.event_type = :event_type
            AND events.start_timestamp_from_beginning = (
                SELECT MAX(start_timestamp_from_beginning) FROM events
                WHERE profile_id = :profile_id AND event_type = :event_type
            )""";

    //language=SQL
    private static final String FIELDS_BY_EVENT = """
            SELECT events.event_type, json(events.fields) FROM events
            WHERE events.profile_id = (:profile_id) AND events.event_type IN (:code)""";

    //language=SQL
    private static final String CONTAINS_EVENT =
            "SELECT COUNT(*) FROM events WHERE profile_id = (:profile_id) AND event_type = (:code)";

    private final String profileId;
    private final DatabaseClient databaseClient;

    public JdbcProfileEventRepository(String profileId, DataSource dataSource) {
        this.profileId = profileId;
        this.databaseClient = new DatabaseClient(dataSource, PROFILE_EVENTS);
    }

    @Override
    public EventStreamerFactory newEventStreamerFactory(EventQueryConfigurer configurer) {
        List<Type> types = configurer.eventTypes();

        QueryBuilderFactory queryBuilderFactory;
        if (types.size() == 1 && types.getFirst() == Type.NATIVE_LEAK) {
            queryBuilderFactory = new NativeLeakQueryBuilderFactory(profileId);
        } else {
            queryBuilderFactory = new DefaultQueryBuilderFactory(profileId);
        }
        return new JdbcEventStreamerFactory(databaseClient, configurer, queryBuilderFactory);
    }

    @Override
    public Optional<GenericRecord> latest(Type type) {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(type)
                .withJsonFields();

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("profile_id", profileId)
                .addValue("event_type", type.code());

        return databaseClient.querySingle(
                StatementLabel.FIND_LATEST_EVENT,
                SINGLE_LATEST_QUERY,
                params,
                new GenericRecordRowMapper(configurer));
    }

    @Override
    public List<GenericRecord> allLatest(Type type) {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(type)
                .withThreads();

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("profile_id", profileId)
                .addValue("event_type", type.code());

        return databaseClient.query(
                StatementLabel.FIND_ALL_LATEST_EVENTS,
                ALL_LATEST_QUERY,
                params,
                new GenericRecordRowMapper(configurer));
    }

    @Override
    public List<JsonNode> eventsByTypeWithFields(Type type) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("profile_id", profileId)
                .addValue("code", type.code());

        return databaseClient.query(
                StatementLabel.FIELDS_WITH_EVENT_TYPE,
                FIELDS_BY_EVENT,
                paramSource,
                (rs, _) -> Json.readTree(rs.getString("json(events.fields)")));
    }

    @Override
    public boolean containsEventType(Type type) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("profile_id", profileId)
                .addValue("code", type.code());

        return databaseClient.queryExists(StatementLabel.CONTAINS_EVENT, CONTAINS_EVENT, paramSource);
    }
}
