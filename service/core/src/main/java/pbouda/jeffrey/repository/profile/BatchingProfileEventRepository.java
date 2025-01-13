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

package pbouda.jeffrey.repository.profile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import pbouda.jeffrey.common.model.profile.Event;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class BatchingProfileEventRepository implements ProfileEventRepository {

    private static final Logger LOG = LoggerFactory.getLogger(BatchingProfileEventRepository.class);

    private static final String INSERT_EVENT = """
            INSERT INTO events (
                event_id,
                event_name,
                timestamp,
                duration,
                samples,
                weight,
                stacktrace_id,
                fields
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final int batchSize;

    private final List<Event> cachedEvents = new ArrayList<>();

    public BatchingProfileEventRepository(JdbcTemplate jdbcTemplate, int batchSize) {
        this.jdbcTemplate = jdbcTemplate;
        this.batchSize = batchSize;
    }

    @Override
    public void insertEvent(Event event) {
        if (cachedEvents.size() < batchSize) {
            cachedEvents.add(event);
        } else {
            flush();
            cachedEvents.clear();
        }
    }

    @Override
    public void flush() {
        long start = System.nanoTime();
        jdbcTemplate.batchUpdate(INSERT_EVENT, cachedEvents, cachedEvents.size(), new EventStatementSetter());
        long millis = Duration.ofNanos(System.nanoTime() - start).toMillis();
        LOG.info("Batch of events has been flushed: size={} elapsed_ms={}", cachedEvents.size(), millis);
    }

    private static class EventStatementSetter implements ParameterizedPreparedStatementSetter<Event> {

        @Override
        public void setValues(PreparedStatement ps, Event event) {
            try {
                ps.setString(1, event.eventId());
                ps.setString(2, event.eventType());
                ps.setLong(3, event.timestamp());
                setNullableLong(ps, 4, event.duration());
                ps.setLong(5, event.samples());
                setNullableLong(ps, 6, event.weight());
                setNullableString(ps, 7, event.stacktraceId());
                setNullableString(ps, 8, event.fields().toString());
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }

        private void setNullableLong(PreparedStatement ps, int index, Long value) throws SQLException {
            if (value != null) {
                ps.setLong(index, value);
            } else {
                ps.setNull(index, Types.BIGINT);
            }
        }

        private void setNullableString(PreparedStatement ps, int index, String value) throws SQLException {
            if (value != null) {
                ps.setString(index, value);
            } else {
                ps.setNull(index, Types.VARCHAR);
            }
        }
    }
}
