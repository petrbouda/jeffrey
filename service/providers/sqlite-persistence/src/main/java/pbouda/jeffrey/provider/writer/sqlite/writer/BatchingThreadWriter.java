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
import pbouda.jeffrey.provider.api.model.EventThread;
import pbouda.jeffrey.provider.writer.sqlite.StatementLabel;
import pbouda.jeffrey.provider.writer.sqlite.client.DatabaseClient;
import pbouda.jeffrey.provider.writer.sqlite.model.EventThreadWithId;

public class BatchingThreadWriter extends BatchingWriter<EventThreadWithId> {

    //language=SQL
    private static final String INSERT_THREADS = """
            INSERT INTO threads (profile_id, thread_id, name, os_id, java_id, is_virtual)
            VALUES (:profile_id, :thread_id, :name, :os_id , :java_id, :is_virtual)""";

    private final String profileId;

    public BatchingThreadWriter(DatabaseClient databaseClient, String profileId, int batchSize) {
        super(EventThreadWithId.class, databaseClient, INSERT_THREADS, batchSize, StatementLabel.INSERT_THREADS);
        this.profileId = profileId;
    }

    @Override
    protected SqlParameterSource queryMapper(EventThreadWithId entity) {
        EventThread eventThread = entity.eventThread();
        return new MapSqlParameterSource()
                .addValue("profile_id", profileId)
                .addValue("thread_id", entity.id())
                .addValue("name", eventThread.name())
                .addValue("os_id", eventThread.osId())
                .addValue("java_id", eventThread.javaId())
                .addValue("is_virtual", eventThread.isVirtual());
    }
}
