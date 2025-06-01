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
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.provider.api.model.EnhancedEventType;

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
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";

    private final String profileId;

    public BatchingEventTypeWriter(JdbcTemplate dataSource, String profileId, int batchSize) {
        super(EnhancedEventType.class, dataSource, INSERT_EVENT_TYPES, batchSize);
        this.profileId = profileId;
    }

    @Override
    protected Object[] queryMapper(EnhancedEventType enhanced) {
        return new Object[]{
                profileId,
                enhanced.eventType().name(),
                enhanced.eventType().label(),
                enhanced.eventType().typeId(),
                enhanced.eventType().description(),
                Json.toString(enhanced.eventType().categories()),
                enhanced.source().getId(),
                enhanced.subtype(),
                enhanced.samples(),
                enhanced.weight(),
                enhanced.eventType().hasStacktrace(),
                enhanced.calculated(),
                mapToJson(enhanced.extras()),
                mapToJson(enhanced.settings()),
                enhanced.eventType().columns().toString()
        };
    }

    private static String mapToJson(Map<String, String> map) {
        if (map != null) {
            return Json.toString(map);
        } else {
            return null;
        }
    }
}
