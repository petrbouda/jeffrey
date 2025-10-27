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

package pbouda.jeffrey.provider.writer.clickhouse;

import pbouda.jeffrey.provider.api.DatabaseWriter;
import pbouda.jeffrey.provider.api.EventWriters;
import pbouda.jeffrey.provider.api.model.writer.EnhancedEventType;
import pbouda.jeffrey.provider.api.model.writer.EventStacktraceTagWithHash;
import pbouda.jeffrey.provider.api.model.writer.EventStacktraceWithHash;
import pbouda.jeffrey.provider.api.model.writer.EventThreadWithId;
import pbouda.jeffrey.provider.api.model.writer.EventWithId;
import pbouda.jeffrey.provider.writer.clickhouse.writer.ClickHouseEventTypeWriter;
import pbouda.jeffrey.provider.writer.clickhouse.writer.ClickHouseEventWriter;
import pbouda.jeffrey.provider.writer.clickhouse.writer.ClickHouseStacktraceTagWriter;
import pbouda.jeffrey.provider.writer.clickhouse.writer.ClickHouseStacktraceWriter;
import pbouda.jeffrey.provider.writer.clickhouse.writer.ClickHouseThreadWriter;

public class ClickHouseEventWriters implements EventWriters {

    // Writers for each table type
    private final ClickHouseEventTypeWriter eventTypeWriter;
    private final ClickHouseEventWriter eventWriter;
    private final ClickHouseStacktraceWriter stacktraceWriter;
    private final ClickHouseStacktraceTagWriter stacktraceTagWriter;
    private final ClickHouseThreadWriter threadWriter;

    private final ClickHouseClient clickHouseClient;

    public ClickHouseEventWriters(ClickHouseClient clickHouseClient, String profileId, int baseBatchSize) {
        this.clickHouseClient = clickHouseClient;
        // Initialize writers with optimized batch sizes for each table type
        this.eventTypeWriter = new ClickHouseEventTypeWriter(clickHouseClient, profileId, baseBatchSize);
        this.eventWriter = new ClickHouseEventWriter(clickHouseClient, profileId, baseBatchSize);
        this.stacktraceWriter = new ClickHouseStacktraceWriter(clickHouseClient, profileId, baseBatchSize);
        this.stacktraceTagWriter = new ClickHouseStacktraceTagWriter(clickHouseClient, profileId, baseBatchSize);
        this.threadWriter = new ClickHouseThreadWriter(clickHouseClient, profileId, baseBatchSize);
    }

    @Override
    public DatabaseWriter<EnhancedEventType> eventTypes() {
        return eventTypeWriter;
    }

    @Override
    public DatabaseWriter<EventWithId> events() {
        return eventWriter;
    }

    @Override
    public DatabaseWriter<EventStacktraceWithHash> stacktraces() {
        return stacktraceWriter;
    }

    @Override
    public DatabaseWriter<EventStacktraceTagWithHash> stacktraceTags() {
        return stacktraceTagWriter;
    }

    @Override
    public DatabaseWriter<EventThreadWithId> threads() {
        return threadWriter;
    }

    @Override
    public void close() {
        eventTypeWriter.close();
        eventWriter.close();
        stacktraceWriter.close();
        stacktraceTagWriter.close();
        threadWriter.close();

        clickHouseClient.optimizeTables();
    }
}
