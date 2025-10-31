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

package pbouda.jeffrey.provider.writer.duckdb.writer;

import org.duckdb.DuckDBAppender;
import org.duckdb.DuckDBConnection;
import pbouda.jeffrey.provider.api.DataSourceUtils;
import pbouda.jeffrey.provider.api.model.Event;
import pbouda.jeffrey.provider.api.model.writer.EventWithId;
import pbouda.jeffrey.provider.writer.sql.StatementLabel;

import javax.sql.DataSource;
import java.time.ZoneOffset;
import java.util.List;

import static pbouda.jeffrey.provider.writer.duckdb.writer.DuckDBAppenderUtils.nullableAppend;

public class DuckDBEventWriter extends DuckDBBatchingWriter<EventWithId> {

    private final String profileId;
    private final DuckDBConnection connection;

    public DuckDBEventWriter(DataSource dataSource, String profileId, int batchSize) {
        super("events", batchSize, StatementLabel.INSERT_EVENTS);
        this.connection = DataSourceUtils.connection(dataSource, DuckDBConnection.class);
        this.profileId = profileId;
    }

    @Override
    public void execute(List<EventWithId> batch) throws Exception {
        try (DuckDBAppender appender = connection.createAppender("events")) {
            for (EventWithId eventWithId : batch) {
                Event event = eventWithId.event();
                appender.beginRow();
                // profile_id - VARCHAR
                appender.append(profileId);
                // event_it - BIGINT
                appender.append(eventWithId.id());
                // event_type - VARCHAR
                appender.append(event.eventType());
                // start_timestamp - TIMESTAMP_MS NOT NULL
                appender.append(event.startTimestamp().atOffset(ZoneOffset.UTC));
                // start_timestamp_from_beginning - BIGINT NOT NULL
                appender.append(event.startTimestampFromBeginning());
                // duration - BIGINT (nullable)
                nullableAppend(appender, event.duration());
                // samples - BIGINT NOT NULL
                appender.append(event.samples());
                // weight - BIGINT (nullable)
                nullableAppend(appender, event.weight());
                // weight_entity - VARCHAR (nullable)
                nullableAppend(appender, event.weightEntity());
                // stack_hash - BIGINT (nullable) - maps from stacktraceId
                nullableAppend(appender, event.stacktraceId());
                // thread_id - BIGINT (nullable) - hash value
                nullableAppend(appender, event.threadId());
                // fields - JSON (nullable)
                nullableAppend(appender, event.fields() != null ? event.fields().toString() : null);
                appender.endRow();
            }
        }
    }

    @Override
    public void close() {
        DataSourceUtils.close(connection);
    }
}
