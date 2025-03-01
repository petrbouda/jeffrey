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

package pbouda.jeffrey.provider.writer.sqlite;

import org.springframework.jdbc.core.JdbcTemplate;
import pbouda.jeffrey.provider.api.model.EnhancedEventType;
import pbouda.jeffrey.provider.writer.sqlite.model.EventStacktraceTagWithId;
import pbouda.jeffrey.provider.writer.sqlite.model.EventStacktraceWithId;
import pbouda.jeffrey.provider.writer.sqlite.model.EventThreadWithId;
import pbouda.jeffrey.provider.writer.sqlite.model.EventWithId;
import pbouda.jeffrey.provider.writer.sqlite.writer.*;

import javax.sql.DataSource;
import java.io.IOException;

public class JdbcWriters implements AutoCloseable {

    private final DatabaseWriter<EnhancedEventType> eventTypeWriter;
    private final DatabaseWriter<EventWithId> eventWriter;
    private final DatabaseWriter<EventStacktraceWithId> stacktraceWriter;
    private final DatabaseWriter<EventStacktraceTagWithId> stacktraceTagWriter;
    private final DatabaseWriter<EventThreadWithId> threadWriter;

    public JdbcWriters(DataSource datasource, String profileId, int batchSize) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(datasource);

        this.eventTypeWriter = new BatchingEventTypeWriter(jdbcTemplate, profileId, batchSize);
        this.eventWriter = new BatchingEventWriter(jdbcTemplate, profileId, batchSize);
        this.stacktraceWriter = new BatchingStacktraceWriter(jdbcTemplate, profileId, batchSize);
        this.stacktraceTagWriter = new BatchingStacktraceTagWriter(jdbcTemplate, profileId, batchSize);
        this.threadWriter = new BatchingThreadWriter(jdbcTemplate, profileId, batchSize);
    }

    public DatabaseWriter<EnhancedEventType> eventTypes() {
        return eventTypeWriter;
    }

    public DatabaseWriter<EventWithId> events() {
        return eventWriter;
    }

    public DatabaseWriter<EventStacktraceWithId> stacktraces() {
        return stacktraceWriter;
    }

    public DatabaseWriter<EventStacktraceTagWithId> stacktraceTags() {
        return stacktraceTagWriter;
    }

    public DatabaseWriter<EventThreadWithId> threads() {
        return threadWriter;
    }

    @Override
    public void close() throws IOException {
        eventTypeWriter.close();
        eventWriter.close();
        stacktraceWriter.close();
        stacktraceTagWriter.close();
        threadWriter.close();
    }
}
