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

import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.model.profile.EventType;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class BatchingEventTypeWriter extends BatchingDatabaseWriter<EventType> {

    private static final String INSERT_EVENT_TYPES = """
            INSERT INTO event_types (
                name,
                label,
                type_id,
                description,
                categories,
                source,
                subtype,
                samples,
                weight,
                extras
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    public BatchingEventTypeWriter(DataSource dataSource, int batchSize) {
        super(EventType.class, dataSource, batchSize, INSERT_EVENT_TYPES);
    }

    @Override
    void mapper(PreparedStatement statement, EventType eventType) throws SQLException {
        statement.setString(1, eventType.name());
        statement.setString(2, eventType.label());
        setNullableLong(statement, 3, eventType.typeId());
        setNullableString(statement, 4, eventType.description());
        setNullableString(statement, 5, Json.toString(eventType.categories()));
        statement.setInt(6, eventType.source().getId());
        statement.setString(7, eventType.subtype());
        statement.setLong(8, eventType.samples());
        setNullableLong(statement, 9, eventType.weight());
        setNullableJson(statement, 10, eventType.extras());
    }
}
