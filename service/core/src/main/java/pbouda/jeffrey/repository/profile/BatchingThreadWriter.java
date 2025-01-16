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

package pbouda.jeffrey.repository.profile;

import pbouda.jeffrey.common.model.profile.EventThread;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class BatchingThreadWriter extends BatchingDatabaseWriter<EventThread> {

    private static final String INSERT_THREADS = """
            INSERT INTO threads (
                thread_id,
                os_id,
                os_name,
                java_id,
                java_name,
                is_virtual
            ) VALUES (?, ?, ?, ?, ?, ?)
            """;

    public BatchingThreadWriter(DataSource dataSource, int batchSize) {
        super(EventThread.class, dataSource, batchSize, INSERT_THREADS);
    }

    @Override
    void mapper(PreparedStatement statement, EventThread thread) throws SQLException {
        statement.setString(1, thread.threadId());
        setNullableLong(statement, 2, thread.osId());
        setNullableString(statement, 3, thread.osName());
        setNullableLong(statement, 4, thread.javaId());
        setNullableString(statement, 5, thread.javaName());
        statement.setBoolean(6, thread.isVirtual());
    }
}
