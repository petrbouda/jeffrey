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

package pbouda.jeffrey.provider.writer.sqlite.writer;

import org.springframework.jdbc.core.JdbcTemplate;
import pbouda.jeffrey.provider.writer.sqlite.model.EventStacktraceTagWithId;

public class BatchingStacktraceTagWriter extends BatchingWriter<EventStacktraceTagWithId> {

    //language=SQL
    private static final String INSERT_STACKTRACE_TAG =
            "INSERT OR IGNORE INTO stacktrace_tags (profile_id, stacktrace_id, tag_id) VALUES (?, ?, ?)";

    private final String profileId;

    public BatchingStacktraceTagWriter(JdbcTemplate jdbcTemplate, String profileId, int batchSize) {
        super(EventStacktraceTagWithId.class, jdbcTemplate, INSERT_STACKTRACE_TAG, batchSize);
        this.profileId = profileId;
    }

    @Override
    protected Object[] queryMapper(EventStacktraceTagWithId entity) {
        return new Object[]{
                profileId,
                entity.id(),
                entity.tag().id()
        };
    }
}
