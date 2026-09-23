/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

import cafe.jeffrey.provider.profile.api.FieldTextWithHash;
import cafe.jeffrey.shared.persistence.StatementLabel;

import org.duckdb.DuckDBAppender;
import org.duckdb.DuckDBConnection;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.Executor;

public class DuckDBFieldTextWriter extends DuckDBBatchingWriter<FieldTextWithHash> {

    public DuckDBFieldTextWriter(Executor executor, DataSource dataSource, int batchSize, BatchFlushLimit flushLimit) {
        super(executor, "field_texts", dataSource, batchSize, StatementLabel.INSERT_FIELD_TEXTS, flushLimit);
    }

    @Override
    public void execute(DuckDBConnection connection, List<FieldTextWithHash> batch) throws Exception {
        try (DuckDBAppender appender = connection.createAppender("field_texts")) {
            for (FieldTextWithHash entity : batch) {
                appender.beginRow();
                // text_hash - BIGINT NOT NULL
                appender.append(entity.hash());
                // text - VARCHAR NOT NULL
                appender.append(entity.text());
                appender.endRow();
            }
        }
    }
}
