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
import pbouda.jeffrey.provider.api.model.Event;
import pbouda.jeffrey.provider.writer.sql.StatementLabel;

import javax.sql.DataSource;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Executor;

import static pbouda.jeffrey.provider.writer.duckdb.writer.DuckDBAppenderUtils.nullableAppend;

public class DuckDBEventWriter extends DuckDBBatchingWriter<Event> {

    private final String profileId;

    public DuckDBEventWriter(Executor executor, DataSource dataSource, String profileId, int batchSize) {
        super(executor, "events", dataSource, batchSize, StatementLabel.INSERT_EVENTS);
        this.profileId = profileId;
    }

    @Override
    public void execute(DuckDBConnection connection, List<Event> batch) throws Exception {
        try (DuckDBAppender appender = connection.createAppender("events")) {
            for (Event event : batch) {
                appender.beginRow();
                // profile_id - VARCHAR
                appender.append(profileId);
                // event_type - VARCHAR
                appender.append(event.eventType());
                // start_timestamp - TIMESTAMP_MS NOT NULL
                appender.append(event.startTimestamp().atOffset(ZoneOffset.UTC));
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
                // thread_hash - BIGINT (nullable) - hash value
                nullableAppend(appender, event.threadId());
                // fields - JSON (nullable)
                nullableAppend(appender, event.fields() != null ? event.fields().toString() : null);
                appender.endRow();
            }
        }
    }
}
