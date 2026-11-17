/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
