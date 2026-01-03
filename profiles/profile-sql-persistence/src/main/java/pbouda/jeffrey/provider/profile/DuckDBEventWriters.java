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

import org.duckdb.DuckDBConnection;
import pbouda.jeffrey.provider.profile.model.writer.EventFrameWithHash;
import pbouda.jeffrey.provider.profile.writer.*;
import pbouda.jeffrey.shared.persistence.DataSourceUtils;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class DuckDBEventWriters implements EventWriters {

    private static final Executor EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final DuckDBEventWriter eventWriter;
    private final DuckDBEventTypeWriter eventTypeWriter;
    private final DuckDBStacktraceWriter stacktraceWriter;
    private final DuckDBThreadWriter threadWriter;
    private final DuckDBFrameWriter frameWriter;

    public DuckDBEventWriters(DataSource dataSource, int batchSize) {
        try {
            this.eventWriter = new DuckDBEventWriter(EXECUTOR, unwrap(dataSource), batchSize);
            this.eventTypeWriter = new DuckDBEventTypeWriter(EXECUTOR, unwrap(dataSource), batchSize);
            this.stacktraceWriter = new DuckDBStacktraceWriter(EXECUTOR, unwrap(dataSource), batchSize);
            this.threadWriter = new DuckDBThreadWriter(EXECUTOR, unwrap(dataSource), batchSize);
            this.frameWriter = new DuckDBFrameWriter(EXECUTOR, unwrap(dataSource), batchSize);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to obtain connections for event writers", e);
        }
    }

    private static DuckDBConnection unwrap(DataSource dataSource) throws SQLException {
        return DataSourceUtils.unwrapConnection(dataSource.getConnection(), DuckDBConnection.class);
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
    }
}
