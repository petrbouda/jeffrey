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

package pbouda.jeffrey.persistence.profile;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.persistence.profile.model.StacktraceRecord;
import pbouda.jeffrey.persistence.profile.model.EventTypeWithFields;
import pbouda.jeffrey.persistence.profile.parser.DbJfrStackTrace;

import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class EventsReadRepository {

    private static final RowMapper<EventTypeWithFields> TYPE_FIELDS_MAPPER = (rs, rowNum) -> {
        String name = rs.getString("name");
        String label = rs.getString("label");
        String fields = rs.getString("fields");
        return new EventTypeWithFields(name, label, Json.readObjectNode(fields));
    };

    private static final RowMapper<StacktraceRecord> EVENT_STACKTRACE_MAPPER = (rs, rowNum) -> {
        long samples = rs.getLong("samples");
        long weight = rs.getLong("weight");
        String frames = rs.getString("frames");
        return new StacktraceRecord(samples, weight, new DbJfrStackTrace(frames));
    };

    private final JdbcTemplate jdbcTemplate;

    private static final String FIELDS_BY_SINGLE_EVENT = """
            SELECT event_types.name, event_types.label, events.fields FROM events
            INNER JOIN event_types ON events.event_name = event_types.name
            WHERE events.event_name = ? LIMIT 1
            """;

    private static final String FIELDS_FOR_ACTIVE_SETTINGS = """
            SELECT event_types.name, event_types.label, events.fields  FROM events
            INNER JOIN event_types ON events.fields->>'id' = event_types.type_id
            WHERE events.event_name = 'jdk.ActiveSetting'
            """;

    private static final String EVENTS_WITH_STACKTRACES = """
            SELECT events.samples, events.weight, stacktraces.frames FROM events
            INNER JOIN stacktraces ON events.stacktrace_id = stacktraces.stacktrace_id
            WHERE events.event_name = ?
            """;

    public EventsReadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<EventTypeWithFields> singleFieldsByEventType(Type type) {
        List<EventTypeWithFields> results = jdbcTemplate.query(FIELDS_BY_SINGLE_EVENT, TYPE_FIELDS_MAPPER, type.code());
        return results.stream().findFirst();
    }

    public List<EventTypeWithFields> activeSettings() {
        return jdbcTemplate.query(FIELDS_FOR_ACTIVE_SETTINGS, TYPE_FIELDS_MAPPER);
    }

    public void consumeStacktraces(Type type, RowCallbackHandler consumer) {
        jdbcTemplate.query(EVENTS_WITH_STACKTRACES, consumer, type.code());
    }
}
