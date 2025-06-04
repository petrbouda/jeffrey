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
import pbouda.jeffrey.provider.api.model.Event;
import pbouda.jeffrey.provider.writer.sqlite.client.DatabaseClient;
import pbouda.jeffrey.provider.writer.sqlite.model.EventWithId;

public class BatchingEventWriter extends BatchingWriter<EventWithId> {

    //language=SQL
    private static final String INSERT_EVENT = """
            INSERT INTO events (
                profile_id,
                event_id,
                event_type,
                timestamp,
                timestamp_from_start,
                duration,
                samples,
                weight,
                weight_entity,
                stacktrace_id,
                thread_id
            ) VALUES (
                :profile_id,
                :event_id,
                :event_type,
                :timestamp,
                :timestamp_from_start,
                :duration,
                :samples,
                :weight,
                :weight_entity,
                :stacktrace_id,
                :thread_id)""";

    private final String profileId;

    public BatchingEventWriter(DatabaseClient databaseClient, String profileId, int batchSize) {
        super(EventWithId.class, databaseClient, INSERT_EVENT, batchSize);
        this.profileId = profileId;
    }

    @Override
    protected SqlParameterSource queryMapper(EventWithId e) {
        Event event = e.event();
        return new MapSqlParameterSource()
                .addValue("profile_id", profileId)
                .addValue("event_id", e.id())
                .addValue("event_type", event.eventType())
                .addValue("timestamp", event.timestamp())
                .addValue("timestamp_from_start", event.timestampFromStart())
                .addValue("duration", event.duration())
                .addValue("samples", event.samples())
                .addValue("weight", event.weight())
                .addValue("weight_entity", event.weightEntity())
                .addValue("stacktrace_id", event.stacktraceId())
                .addValue("thread_id", event.threadId());
    }
}
