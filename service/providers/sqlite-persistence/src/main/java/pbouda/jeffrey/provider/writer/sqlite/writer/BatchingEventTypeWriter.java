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
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.provider.api.model.EnhancedEventType;
import pbouda.jeffrey.provider.writer.sqlite.client.DatabaseClient;

import java.util.Map;

public class BatchingEventTypeWriter extends BatchingWriter<EnhancedEventType> {

    //language=SQL
    private static final String INSERT_EVENT_TYPES = """
            INSERT INTO event_types (
                profile_id,
                name,
                label,
                type_id,
                description,
                categories,
                source,
                subtype,
                samples,
                weight,
                has_stacktrace,
                calculated,
                extras,
                settings,
                columns
            ) VALUES (
                :profile_id,
                :name,
                :label,
                :type_id,
                :description,
                :categories,
                :source,
                :subtype,
                :samples,
                :weight,
                :has_stacktrace,
                :calculated,
                :extras,
                :settings,
                :columns)""";

    private final String profileId;

    public BatchingEventTypeWriter(DatabaseClient databaseClient, String profileId, int batchSize) {
        super(EnhancedEventType.class, databaseClient, INSERT_EVENT_TYPES, batchSize);
        this.profileId = profileId;
    }

    @Override
    protected SqlParameterSource queryMapper(EnhancedEventType enhanced) {
        return new MapSqlParameterSource()
                .addValue("profile_id", profileId)
                .addValue("name", enhanced.eventType().name())
                .addValue("label", enhanced.eventType().label())
                .addValue("type_id", enhanced.eventType().typeId())
                .addValue("description", enhanced.eventType().description())
                .addValue("categories", Json.toString(enhanced.eventType().categories()))
                .addValue("source", enhanced.source().getId())
                .addValue("subtype", enhanced.subtype())
                .addValue("samples", enhanced.samples())
                .addValue("weight", enhanced.weight())
                .addValue("has_stacktrace", enhanced.containsStackTraces())
                .addValue("calculated", enhanced.calculated())
                .addValue("extras", mapToJson(enhanced.extras()))
                .addValue("settings", mapToJson(enhanced.settings()))
                .addValue("columns", enhanced.eventType().columns().toString());
    }

    private static String mapToJson(Map<String, String> map) {
        if (map != null) {
            return Json.toString(map);
        } else {
            return null;
        }
    }
}
