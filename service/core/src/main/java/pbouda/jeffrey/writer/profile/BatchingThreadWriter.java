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

package pbouda.jeffrey.writer.profile;

import pbouda.jeffrey.common.model.profile.EventThread;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class BatchingThreadWriter extends BatchingDatabaseWriter<EventThread> {

    //language=SQL
    private static final String INSERT_THREADS = """
            INSERT INTO threads (
                thread_id,
                name,
                os_id,
                java_id,
                is_virtual
            ) VALUES (?, ?, ?, ?, ?)
            """;

    public BatchingThreadWriter(DataSource dataSource, int batchSize) {
        super(EventThread.class, dataSource, batchSize, INSERT_THREADS);
    }

    @Override
    void mapper(PreparedStatement statement, EventThread thread) throws SQLException {
        statement.setLong(1, thread.threadId());
        statement.setString(2, thread.name());
        statement.setLong(3, thread.osId());
        setNullableLong(statement, 4, thread.javaId());
        statement.setBoolean(5, thread.isVirtual());
    }
}
