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
import pbouda.jeffrey.provider.api.model.EventFields;
import pbouda.jeffrey.provider.writer.sqlite.client.DatabaseClient;

public class BatchingEventFieldsWriter extends BatchingWriter<EventFields> {

    //language=SQL
    private static final String INSERT_FIELDS =
            "INSERT INTO event_fields(profile_id, event_id, fields) VALUES (:profile_id, :event_id, :fields)";

    private final String profileId;

    public BatchingEventFieldsWriter(DatabaseClient databaseClient, String profileId, int batchSize) {
        super(EventFields.class, databaseClient, INSERT_FIELDS, batchSize);
        this.profileId = profileId;
    }

    @Override
    protected SqlParameterSource queryMapper(EventFields entity) {
        return new MapSqlParameterSource()
                .addValue("profile_id", profileId)
                .addValue("event_id", entity.eventId())
                .addValue("fields", entity.fields().toString());
    }
}
