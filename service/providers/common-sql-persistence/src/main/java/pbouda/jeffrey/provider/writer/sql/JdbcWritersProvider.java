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

package pbouda.jeffrey.provider.writer.sql;

import pbouda.jeffrey.provider.api.WritersProvider;
import pbouda.jeffrey.provider.writer.sql.client.DatabaseClient;
import pbouda.jeffrey.provider.writer.sql.writer.*;

public class JdbcWritersProvider implements WritersProvider {

    private final BatchingEventTypeWriter eventTypeWriter;
    private final BatchingEventWriter eventWriter;
    private final BatchingStacktraceWriter stacktraceWriter;
    private final BatchingStacktraceTagWriter stacktraceTagWriter;
    private final BatchingThreadWriter threadWriter;

    public JdbcWritersProvider(DatabaseClient databaseClient, String profileId, int batchSize) {
        this.eventTypeWriter = new BatchingEventTypeWriter(databaseClient, profileId, batchSize);
        this.eventWriter = new BatchingEventWriter(databaseClient, profileId, batchSize);
        this.stacktraceWriter = new BatchingStacktraceWriter(databaseClient, profileId, batchSize);
        this.stacktraceTagWriter = new BatchingStacktraceTagWriter(databaseClient, profileId, batchSize);
        this.threadWriter = new BatchingThreadWriter(databaseClient, profileId, batchSize);
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
    public void close() {
        eventTypeWriter.close();
        eventWriter.close();
        stacktraceWriter.close();
        stacktraceTagWriter.close();
        threadWriter.close();
    }
}
