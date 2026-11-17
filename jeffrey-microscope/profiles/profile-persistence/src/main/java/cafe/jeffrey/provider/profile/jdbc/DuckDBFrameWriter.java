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
import cafe.jeffrey.provider.profile.api.EventFrame;
import cafe.jeffrey.provider.profile.api.EventFrameWithHash;
import cafe.jeffrey.shared.persistence.StatementLabel;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.Executor;

public class DuckDBFrameWriter extends DuckDBBatchingWriter<EventFrameWithHash> {

    public DuckDBFrameWriter(Executor executor, DataSource dataSource, int batchSize, BatchFlushLimit flushLimit) {
        super(executor, "frames", dataSource, batchSize, StatementLabel.INSERT_FRAMES, flushLimit);
    }

    @Override
    public void execute(DuckDBConnection connection, List<EventFrameWithHash> batch) throws Exception {
        try (DuckDBAppender appender = connection.createAppender("frames")) {
            for (EventFrameWithHash entity : batch) {
                EventFrame frame = entity.frame();

                appender.beginRow();
                // frame_hash - BIGINT NOT NULL
                appender.append(entity.hash());
                // class_name - VARCHAR
                appender.append(frame.clazz());
                // method_name - VARCHAR
                appender.append(frame.method());
                // frame_type - VARCHAR
                appender.append(frame.type());
                // line_number - INTEGER
                appender.append((int) frame.line());
                // bytecode_index - INTEGER
                appender.append((int) frame.bci());
                // hidden_class_id - VARCHAR (NULL for ordinary classes)
                appender.append(frame.hiddenClassId());
                appender.endRow();
            }
        }
    }
}
