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

package pbouda.jeffrey.provider.writer.duckdb.writer;

import org.duckdb.DuckDBAppender;
import org.duckdb.DuckDBConnection;
import pbouda.jeffrey.provider.api.model.EventThread;
import pbouda.jeffrey.provider.api.model.writer.EventThreadWithHash;
import pbouda.jeffrey.provider.writer.sql.StatementLabel;

import javax.sql.DataSource;
import java.util.List;

import static pbouda.jeffrey.provider.writer.duckdb.writer.DuckDBAppenderUtils.nullableAppend;

public class DuckDBThreadWriter extends DuckDBBatchingWriter<EventThreadWithHash> {

    private final String profileId;

    public DuckDBThreadWriter(
            AsyncSingleWriter asyncSingleWriter, DataSource dataSource, String profileId, int batchSize) {
        super(asyncSingleWriter, "threads", dataSource, batchSize, StatementLabel.INSERT_THREADS);
        this.profileId = profileId;
    }

    @Override
    public void execute(DuckDBConnection connection, List<EventThreadWithHash> batch) throws Exception {
        try (DuckDBAppender appender = connection.createAppender("threads")) {
            for (EventThreadWithHash entity : batch) {
                EventThread thread = entity.eventThread();

                appender.beginRow();
                // profile_id - VARCHAR NOT NULL
                appender.append(profileId);
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
        }
    }
}
