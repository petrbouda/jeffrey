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

import org.duckdb.DuckDBAppender;
import org.duckdb.DuckDBConnection;
import cafe.jeffrey.provider.profile.api.EventStacktraceWithHash;
import cafe.jeffrey.shared.persistence.StatementLabel;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.Executor;

public class DuckDBStacktraceWriter extends DuckDBBatchingWriter<EventStacktraceWithHash> {

    public DuckDBStacktraceWriter(Executor executor, DataSource dataSource, int batchSize, BatchFlushLimit flushLimit) {
        super(executor, "stacktraces", dataSource, batchSize, StatementLabel.INSERT_STACKTRACES, flushLimit);
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
