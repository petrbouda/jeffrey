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

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.provider.api.repository.EventQueryConfigurer;
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;
import pbouda.jeffrey.provider.api.streamer.EventStreamerFactory;
import pbouda.jeffrey.provider.api.streamer.model.GenericRecord;
import pbouda.jeffrey.provider.writer.sqlite.query.GenericRecordRowMapper;
import pbouda.jeffrey.provider.writer.sqlite.query.JdbcEventStreamerFactory;

import java.util.List;
import java.util.Optional;

public class JdbcProfileEventRepository implements ProfileEventRepository {

    //language=SQL
    private final String SINGLE_LATEST_QUERY = """
            SELECT events.*, event_fields.fields FROM events
            INNER JOIN event_fields ON
                events.profile_id = event_fields.profile_id AND events.event_id = event_fields.event_id
            WHERE events.profile_id = :profile_id AND events.event_type = :event_type
            ORDER BY events.timestamp DESC LIMIT 1
            """;

    //language=SQL
    private final String ALL_LATEST_QUERY = """
            SELECT events.*, event_fields.fields FROM events
            INNER JOIN event_fields ON
                events.profile_id = event_fields.profile_id AND events.event_id = event_fields.event_id
            WHERE events.profile_id = :profile_id AND events.event_type = :event_type
            AND events.timestamp = (
                SELECT MAX(timestamp) FROM events
                WHERE profile_id = :profile_id AND event_type = :event_type
            )
            """;

    private final String profileId;
    private final JdbcTemplate jdbcTemplate;

    public JdbcProfileEventRepository(String profileId, JdbcTemplate jdbcTemplate) {
        this.profileId = profileId;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public EventStreamerFactory newEventStreamerFactory() {
        return new JdbcEventStreamerFactory(jdbcTemplate, profileId);
    }

    @Override
    public Optional<GenericRecord> latest(Type type) {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(type)
                .withJsonFields();

        return JdbcClient.create(jdbcTemplate)
                .sql(SINGLE_LATEST_QUERY)
                .param("profile_id", profileId)
                .param("event_type", type.code())
                .query(new GenericRecordRowMapper(configurer))
                .optional();
    }

    @Override
    public List<GenericRecord> allLatest(Type type) {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(type)
                .withJsonFields();

        return JdbcClient.create(jdbcTemplate)
                .sql(ALL_LATEST_QUERY)
                .param("profile_id", profileId)
                .param("event_type", type.code())
                .query(new GenericRecordRowMapper(configurer))
                .list();
    }
}
