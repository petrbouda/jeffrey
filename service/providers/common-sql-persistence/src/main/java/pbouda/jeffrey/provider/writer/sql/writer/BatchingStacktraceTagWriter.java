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

package pbouda.jeffrey.provider.writer.sql.writer;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import pbouda.jeffrey.provider.writer.sql.StatementLabel;
import pbouda.jeffrey.provider.writer.sql.client.DatabaseClient;
import pbouda.jeffrey.provider.api.model.writer.EventStacktraceTagWithHash;

public class BatchingStacktraceTagWriter extends BatchingWriter<EventStacktraceTagWithHash> {

    //language=SQL
    private static final String INSERT_STACKTRACE_TAG = """
            INSERT INTO stacktrace_tags (profile_id, stacktrace_id, tag_id)
            VALUES (:profile_id, :stacktrace_id, :tag_id)
            ON CONFLICT DO NOTHING""";

    private final String profileId;

    public BatchingStacktraceTagWriter(DatabaseClient databaseClient, String profileId, int batchSize) {
        super(EventStacktraceTagWithHash.class,
                databaseClient,
                INSERT_STACKTRACE_TAG,
                batchSize,
                StatementLabel.INSERT_STACKTRACE_TAGS);

        this.profileId = profileId;
    }

    @Override
    protected SqlParameterSource queryMapper(EventStacktraceTagWithHash entity) {
        return new MapSqlParameterSource()
                .addValue("profile_id", profileId)
                .addValue("stacktrace_id", entity.id())
                .addValue("tag_id", entity.tag().id());
    }
}
