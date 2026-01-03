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

package pbouda.jeffrey.provider.profile.writer;

import org.duckdb.DuckDBAppender;
import org.duckdb.DuckDBConnection;
import pbouda.jeffrey.provider.profile.model.writer.EventStacktraceWithHash;
import pbouda.jeffrey.shared.persistence.StatementLabel;

import java.util.List;
import java.util.concurrent.Executor;

public class DuckDBStacktraceWriter extends DuckDBBatchingWriter<EventStacktraceWithHash> {

    public DuckDBStacktraceWriter(Executor executor, DuckDBConnection connection, int batchSize) {
        super(executor, "stacktraces", connection, batchSize, StatementLabel.INSERT_STACKTRACES);
    }

    @Override
    public void execute(DuckDBConnection connection, List<EventStacktraceWithHash> batch) throws Exception {
        try (DuckDBAppender appender = connection.createAppender("stacktraces")) {
            for (EventStacktraceWithHash entity : batch) {
                appender.beginRow();
                // stacktrace_hash - BIGINT NOT NULL
                appender.append(entity.hash());
                // type_id - INTEGER NOT NULL
                appender.append(entity.type().id());
                // frame_hashes - BIGINT[]
                appender.append(entity.frameHashes());
                // tag_ids - INTEGER[]
                appender.append(entity.tags());
                appender.endRow();
            }
        }
    }
}
