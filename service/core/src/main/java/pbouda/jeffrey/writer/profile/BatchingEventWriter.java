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

package pbouda.jeffrey.writer.profile;

import pbouda.jeffrey.common.model.profile.Event;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class BatchingEventWriter extends BatchingDatabaseWriter<Event> {

    //language=SQL
    private static final String INSERT_EVENT = """
            INSERT INTO events (
                event_id,
                event_name,
                timestamp,
                timestamp_from_start,
                duration,
                samples,
                weight,
                weight_entity,
                stacktrace_id,
                thread_id,
                fields
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    public BatchingEventWriter(DataSource dataSource, int batchSize) {
        super(Event.class, dataSource, batchSize, INSERT_EVENT);
    }

    @Override
    void mapper(PreparedStatement statement, Event event) throws SQLException {
        statement.setLong(1, event.eventId());
        statement.setString(2, event.eventType());
        statement.setLong(3, event.timestamp());
        statement.setLong(4, event.timestampFromStart());
        setNullableLong(statement, 5, event.duration());
        statement.setLong(6, event.samples());
        setNullableLong(statement, 7, event.weight());
        setNullableString(statement, 8, event.weightEntity());
        setNullableLong(statement, 9, event.stacktraceId());
        setNullableLong(statement, 10, event.threadId());
        setNullableString(statement, 11, event.fields().toString());
    }
}
