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
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * Writer set for a single profile database. The high-volume {@code events} table is written
 * by the columnar {@link DuckDBArrowEventWriter}; the low-volume tables (event types,
 * stacktraces, threads, frames) stay on the row-based appender writers.
 */
public class DuckDBEventWriters implements EventWriters {

    private final DuckDBArrowEventWriter eventWriter;
    private final DuckDBEventTypeWriter eventTypeWriter;
    private final DuckDBStacktraceWriter stacktraceWriter;
    private final DuckDBThreadWriter threadWriter;
    private final DuckDBFrameWriter frameWriter;

    /**
     * @param executor            shared executor for the appender-based writers
     * @param eventsFlushExecutor dedicated executor for events flushes — bulk columnar inserts do not
     *                            benefit from concurrency, a single flush thread keeps the parser
     *                            threads unblocked while serializing the inserts
     * @param dataSource          profile database
     * @param batchSize           batch size for the appender-based writers
     * @param eventsBatchSize     batch size for the events table — larger than the appender batches
     *                            to amortize the per-INSERT overhead of the bulk path
     */
    public DuckDBEventWriters(
            ExecutorService executor,
            Executor eventsFlushExecutor,
            DataSource dataSource,
            int batchSize,
            int eventsBatchSize) {

        this.eventWriter = new DuckDBArrowEventWriter(eventsFlushExecutor, dataSource, eventsBatchSize);
        this.eventTypeWriter = new DuckDBEventTypeWriter(executor, dataSource, batchSize);
        this.stacktraceWriter = new DuckDBStacktraceWriter(executor, dataSource, batchSize);
        this.threadWriter = new DuckDBThreadWriter(executor, dataSource, batchSize);
        this.frameWriter = new DuckDBFrameWriter(executor, dataSource, batchSize);
    }

    @Override
    public DatabaseWriter<Event> events() {
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
    public void close() {
        eventTypeWriter.close();
        eventWriter.close();
        stacktraceWriter.close();
        threadWriter.close();
        frameWriter.close();
    }
}
