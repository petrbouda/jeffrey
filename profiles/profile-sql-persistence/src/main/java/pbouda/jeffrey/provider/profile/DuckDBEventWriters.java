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

package pbouda.jeffrey.provider.profile;

import pbouda.jeffrey.provider.profile.model.writer.EventFrameWithHash;
import pbouda.jeffrey.provider.profile.writer.*;

import javax.sql.DataSource;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DuckDBEventWriters implements EventWriters {

    private final ExecutorService executor = Executors.newFixedThreadPool(20);

    private final DuckDBEventWriter eventWriter;
    private final DuckDBEventTypeWriter eventTypeWriter;
    private final DuckDBStacktraceWriter stacktraceWriter;
    private final DuckDBThreadWriter threadWriter;
    private final DuckDBFrameWriter frameWriter;

    public DuckDBEventWriters(DataSource dataSource, int batchSize) {
        this.eventWriter = new DuckDBEventWriter(executor, dataSource, batchSize);
        this.eventTypeWriter = new DuckDBEventTypeWriter(executor, dataSource, batchSize);
        this.stacktraceWriter = new DuckDBStacktraceWriter(executor, dataSource, batchSize);
        this.threadWriter = new DuckDBThreadWriter(executor, dataSource, batchSize);
        this.frameWriter = new DuckDBFrameWriter(executor, dataSource, batchSize);
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
    public void close() {
        eventTypeWriter.close();
        eventWriter.close();
        stacktraceWriter.close();
        threadWriter.close();
        frameWriter.close();

        executor.shutdown();
    }
}
