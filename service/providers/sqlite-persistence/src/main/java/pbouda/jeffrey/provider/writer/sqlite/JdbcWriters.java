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
import pbouda.jeffrey.provider.writer.sqlite.writer.*;

import javax.sql.DataSource;
import java.io.IOException;

public class JdbcWriters implements AutoCloseable {

    private final BatchingEventTypeWriter eventTypeWriter;
    private final BatchingEventWriter eventWriter;
    private final BatchingStacktraceWriter stacktraceWriter;
    private final BatchingStacktraceTagWriter stacktraceTagWriter;
    private final BatchingThreadWriter threadWriter;

    public JdbcWriters(DataSource datasource, String profileId, int batchSize) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(datasource);

        this.eventTypeWriter = new BatchingEventTypeWriter(jdbcTemplate, profileId, batchSize);
        this.eventWriter = new BatchingEventWriter(jdbcTemplate, profileId, batchSize);
        this.stacktraceWriter = new BatchingStacktraceWriter(jdbcTemplate, profileId, batchSize);
        this.stacktraceTagWriter = new BatchingStacktraceTagWriter(jdbcTemplate, profileId, batchSize);
        this.threadWriter = new BatchingThreadWriter(jdbcTemplate, profileId, batchSize);
    }

    public BatchingEventTypeWriter eventTypes() {
        return eventTypeWriter;
    }

    public BatchingEventWriter events() {
        return eventWriter;
    }

    public BatchingStacktraceWriter stacktraces() {
        return stacktraceWriter;
    }

    public BatchingStacktraceTagWriter stacktraceTags() {
        return stacktraceTagWriter;
    }

    public BatchingThreadWriter threads() {
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
