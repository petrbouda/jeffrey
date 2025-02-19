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

import pbouda.jeffrey.common.model.profile.EventStacktraceTag;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class BatchingStacktraceTagWriter extends BatchingDatabaseWriter<EventStacktraceTag> {

    //language=SQL
    private static final String INSERT_STACKTRACE_TAG = """
            INSERT OR IGNORE INTO stacktrace_tags (
                stacktrace_id,
                tag_id
            ) VALUES (?, ?)
            """;

    public BatchingStacktraceTagWriter(DataSource dataSource, int batchSize) {
        super(EventStacktraceTag.class, dataSource, batchSize, INSERT_STACKTRACE_TAG);
    }

    @Override
    void mapper(PreparedStatement statement, EventStacktraceTag entity) throws SQLException {
        statement.setLong(1, entity.stacktrace().stacktraceId());
        statement.setInt(2, entity.tag().id());
    }
}
