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

import pbouda.jeffrey.common.model.profile.Event;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class BatchingEventWriter extends BatchingDatabaseWriter<Event> {

    private static final String INSERT_EVENT = """
            INSERT INTO events (
                event_name,
                timestamp,
                duration,
                samples,
                weight,
                stacktrace_id,
                fields
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    public BatchingEventWriter(DataSource dataSource, int batchSize) {
        super(Event.class, dataSource, batchSize, INSERT_EVENT);
    }

    @Override
    void mapper(PreparedStatement statement, Event event) throws SQLException {
        statement.setString(1, event.eventType());
        statement.setLong(2, event.timestamp());
        setNullableLong(statement, 3, event.duration());
        statement.setLong(4, event.samples());
        setNullableLong(statement, 5, event.weight());
        setNullableString(statement, 6, event.stacktraceId());
        setNullableString(statement, 7, event.fields().toString());
    }
}
