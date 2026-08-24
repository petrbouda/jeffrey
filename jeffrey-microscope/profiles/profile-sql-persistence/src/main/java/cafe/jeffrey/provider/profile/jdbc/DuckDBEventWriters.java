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

package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.provider.profile.api.*;

import cafe.jeffrey.provider.profile.api.EventFrameWithHash;
import cafe.jeffrey.provider.profile.jdbc.*;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.concurrent.ExecutorService;

public class DuckDBEventWriters implements EventWriters {

    private final DuckDBEventWriter eventWriter;
    private final DuckDBEventTypeWriter eventTypeWriter;
    private final DuckDBStacktraceWriter stacktraceWriter;
    private final DuckDBThreadWriter threadWriter;
    private final DuckDBFrameWriter frameWriter;
    private final DuckDBFieldTextWriter fieldTextWriter;

    /**
     * @param flushLimit shared with every other writer set of the same profile, so the bound is on
     *                   what one ingest has in flight rather than on what one table does
     */
    public DuckDBEventWriters(
            ExecutorService executor,
            DataSource dataSource,
            int batchSize,
            Instant profilingStartedAt,
            BatchFlushLimit flushLimit) {

        this.eventWriter = new DuckDBEventWriter(executor, dataSource, batchSize, profilingStartedAt, flushLimit);
        this.eventTypeWriter = new DuckDBEventTypeWriter(executor, dataSource, batchSize, flushLimit);
        this.stacktraceWriter = new DuckDBStacktraceWriter(executor, dataSource, batchSize, flushLimit);
        this.threadWriter = new DuckDBThreadWriter(executor, dataSource, batchSize, flushLimit);
        this.frameWriter = new DuckDBFrameWriter(executor, dataSource, batchSize, flushLimit);
        this.fieldTextWriter = new DuckDBFieldTextWriter(executor, dataSource, batchSize, flushLimit);
    }

    @Override
    public DuckDBEventWriter events() {
        return eventWriter;
    }

    @Override
    public DuckDBEventTypeWriter eventTypes() {
        return eventTypeWriter;
    }

    @Override
    public DuckDBStacktraceWriter stacktraces() {
        return stacktraceWriter;
    }

    @Override
    public DuckDBThreadWriter threads() {
        return threadWriter;
    }

    @Override
    public DatabaseWriter<EventFrameWithHash> frames() {
        return frameWriter;
    }

    @Override
    public DatabaseWriter<FieldTextWithHash> fieldTexts() {
        return fieldTextWriter;
    }

    @Override
    public void close() {
        eventTypeWriter.close();
        eventWriter.close();
        stacktraceWriter.close();
        threadWriter.close();
        frameWriter.close();
        fieldTextWriter.close();
    }
}
