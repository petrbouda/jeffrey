/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.provider.profile.api.FieldTextWithHash;
import cafe.jeffrey.shared.persistence.StatementLabel;

import org.duckdb.DuckDBAppender;
import org.duckdb.DuckDBConnection;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.Executor;

public class DuckDBFieldTextWriter extends DuckDBBatchingWriter<FieldTextWithHash> {

    public DuckDBFieldTextWriter(Executor executor, DataSource dataSource, int batchSize) {
        super(executor, "field_texts", dataSource, batchSize, StatementLabel.INSERT_FIELD_TEXTS);
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
