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
import pbouda.jeffrey.provider.profile.model.EventFrame;
import pbouda.jeffrey.provider.profile.model.writer.EventFrameWithHash;
import pbouda.jeffrey.shared.persistence.StatementLabel;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.Executor;

public class DuckDBFrameWriter extends DuckDBBatchingWriter<EventFrameWithHash> {

    public DuckDBFrameWriter(Executor executor, DataSource dataSource, int batchSize) {
        super(executor, "frames", dataSource, batchSize, StatementLabel.INSERT_FRAMES);
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
                appender.endRow();
            }
        }
    }
}
