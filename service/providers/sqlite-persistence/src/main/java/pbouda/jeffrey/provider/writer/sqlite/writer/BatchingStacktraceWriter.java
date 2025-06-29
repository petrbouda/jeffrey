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

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import pbouda.jeffrey.provider.writer.sqlite.StatementLabel;
import pbouda.jeffrey.provider.writer.sqlite.client.DatabaseClient;
import pbouda.jeffrey.provider.writer.sqlite.model.EventStacktraceWithId;

public class BatchingStacktraceWriter extends BatchingWriter<EventStacktraceWithId> {

    //language=SQL
    private static final String INSERT_STACKTRACE = """
            INSERT INTO stacktraces (profile_id, stacktrace_id, type_id, frames)
            VALUES (:profile_id, :stacktrace_id, :type_id, :frames)""";

    private final String profileId;

    public BatchingStacktraceWriter(DatabaseClient databaseClient, String profileId, int batchSize) {
        super(EventStacktraceWithId.class,
                databaseClient,
                INSERT_STACKTRACE,
                batchSize,
                StatementLabel.INSERT_STACKTRACES);

        this.profileId = profileId;
    }

    @Override
    protected SqlParameterSource queryMapper(EventStacktraceWithId entity) {
        return new MapSqlParameterSource()
                .addValue("profile_id", profileId)
                .addValue("stacktrace_id", entity.id())
                .addValue("type_id", entity.eventStacktrace().type().id())
                .addValue("frames", entity.eventStacktrace().frames());
    }
}
