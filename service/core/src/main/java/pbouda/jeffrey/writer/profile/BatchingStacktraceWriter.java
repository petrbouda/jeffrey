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

import pbouda.jeffrey.common.model.profile.EventStacktrace;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class BatchingStacktraceWriter extends BatchingDatabaseWriter<EventStacktrace> {

    //language=SQL
    private static final String INSERT_STACKTRACE = """
            INSERT INTO stacktraces (
                stacktrace_id,
                type_id,
                frames
            ) VALUES (?, ?, ?)
            """;

    public BatchingStacktraceWriter(DataSource dataSource, int batchSize) {
        super(EventStacktrace.class, dataSource, batchSize, INSERT_STACKTRACE);
    }

    @Override
    void mapper(PreparedStatement statement, EventStacktrace stacktrace) throws SQLException {
        statement.setLong(1, stacktrace.stacktraceId());
        statement.setInt(2, stacktrace.type().id());
        statement.setString(3, stacktrace.frames());
    }
}
