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
import pbouda.jeffrey.provider.api.DataSourceUtils;
import pbouda.jeffrey.provider.api.model.EventFrame;
import pbouda.jeffrey.provider.api.model.writer.EventFrameWithHash;
import pbouda.jeffrey.provider.writer.sql.StatementLabel;

import javax.sql.DataSource;
import java.util.List;

public class DuckDBFrameWriter extends DuckDBBatchingWriter<EventFrameWithHash> {

    private final String profileId;
    private final DuckDBConnection connection;

    public DuckDBFrameWriter(DataSource dataSource, String profileId, int batchSize) {
        super("frames", batchSize, StatementLabel.INSERT_FRAMES);
        this.profileId = profileId;
        this.connection = DataSourceUtils.connection(dataSource, DuckDBConnection.class);
    }

    @Override
    public void execute(List<EventFrameWithHash> batch) throws Exception {
        try (DuckDBAppender appender = connection.createAppender("frames")) {
            for (EventFrameWithHash entity : batch) {
                EventFrame frame = entity.frame();

                appender.beginRow();
                // profile_id - VARCHAR NOT NULL
                appender.append(profileId);
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
                appender.endRow();
            }
        }
    }

    @Override
    public void close() {
        DataSourceUtils.close(connection);
    }
}
