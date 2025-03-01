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
import pbouda.jeffrey.provider.api.model.EventThread;
import pbouda.jeffrey.provider.writer.sqlite.model.EventThreadWithId;

public class BatchingThreadWriter extends BatchingWriter<EventThreadWithId> {

    //language=SQL
    private static final String INSERT_THREADS = """
            INSERT INTO threads (
                profile_id,
                thread_id,
                name,
                os_id,
                java_id,
                is_virtual
            ) VALUES (?, ?, ?, ?, ?, ?)
            """;

    private final String profileId;

    public BatchingThreadWriter(JdbcTemplate jdbcTemplate, String profileId, int batchSize) {
        super(EventThreadWithId.class, jdbcTemplate, INSERT_THREADS, batchSize);
        this.profileId = profileId;
    }

    @Override
    protected Object[] queryMapper(EventThreadWithId entity) {
        EventThread eventThread = entity.eventThread();
        return new Object[]{
                profileId,
                entity.id(),
                eventThread.name(),
                eventThread.osId(),
                eventThread.javaId(),
                eventThread.isVirtual()
        };
    }
}
