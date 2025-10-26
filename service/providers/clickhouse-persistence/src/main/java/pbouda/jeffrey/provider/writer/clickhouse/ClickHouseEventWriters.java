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
import pbouda.jeffrey.provider.api.model.writer.EventStacktraceTagWithId;
import pbouda.jeffrey.provider.api.model.writer.EventStacktraceWithId;
import pbouda.jeffrey.provider.api.model.writer.EventThreadWithId;
import pbouda.jeffrey.provider.api.model.writer.EventWithId;

public class ClickHouseEventWriters implements EventWriters {

    private final ClickHouseDatabaseClient databaseClient;
    private final String profileId;
    private final int batchSize;

    public ClickHouseEventWriters(ClickHouseDatabaseClient databaseClient, String profileId, int batchSize) {
        this.databaseClient = databaseClient;
        this.profileId = profileId;
        this.batchSize = batchSize;
    }

    @Override
    public DatabaseWriter<EnhancedEventType> eventTypes() {
        return null;
    }

    @Override
    public DatabaseWriter<EventWithId> events() {
        return null;
    }

    @Override
    public DatabaseWriter<EventStacktraceWithId> stacktraces() {
        return null;
    }

    @Override
    public DatabaseWriter<EventStacktraceTagWithId> stacktraceTags() {
        return null;
    }

    @Override
    public DatabaseWriter<EventThreadWithId> threads() {
        return null;
    }

    @Override
    public void close() {

    }
}
