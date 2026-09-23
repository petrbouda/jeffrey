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
import cafe.jeffrey.provider.profile.api.EventThread;
import cafe.jeffrey.provider.profile.api.EventThreadWithHash;
import cafe.jeffrey.shared.persistence.StatementLabel;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.Executor;

import static cafe.jeffrey.provider.profile.jdbc.DuckDBAppenderUtils.nullableAppend;

public class DuckDBThreadWriter extends DuckDBBatchingWriter<EventThreadWithHash> {

    public DuckDBThreadWriter(Executor executor, DataSource dataSource, int batchSize, BatchFlushLimit flushLimit) {
        super(executor, "threads", dataSource, batchSize, StatementLabel.INSERT_THREADS, flushLimit);
    }

    @Override
    public void execute(DuckDBConnection connection, List<EventThreadWithHash> batch) throws Exception {
        try (DuckDBAppender appender = connection.createAppender("threads")) {
            for (EventThreadWithHash entity : batch) {
                EventThread thread = entity.eventThread();

                appender.beginRow();
                // thread_hash - BIGINT NOT NULL
                appender.append(entity.hash());
                // name - VARCHAR NOT NULL
                appender.append(thread.name());
                // os_id - BIGINT (nullable)
                nullableAppend(appender, thread.osId());
                // java_id - BIGINT (nullable)
                nullableAppend(appender, thread.javaId());
                // is_virtual - BOOLEAN NOT NULL
                appender.append(thread.isVirtual());
                appender.endRow();
            }
            appender.flush();
        }
    }
}
