/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.provider.profile.api.*;

import tools.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.RowMapper;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.jfrparser.api.type.JfrMethod;
import cafe.jeffrey.jfrparser.api.type.JfrThread;
import cafe.jeffrey.jfrparser.api.type.JfrMethodImpl;
import cafe.jeffrey.jfrparser.api.type.JfrThreadImpl;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.api.GenericRecord;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;

public class GenericRecordRowMapper implements RowMapper<GenericRecord> {

    private final boolean useThreads;
    private final boolean useEventTypeInfo;
    private final boolean useJsonFields;

    public GenericRecordRowMapper(EventQueryConfigurer configurer) {
        this.useThreads = configurer.threads();
        this.useEventTypeInfo = configurer.eventTypeInfo();
        this.useJsonFields = configurer.jsonFields();
    }

    @Override
    public GenericRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        String eventType = rs.getString("event_type");
        Instant timestamp = instant(rs, "start_timestamp");
        long timestampFromStart = rs.getLong("start_timestamp_from_beginning");

        Long duration = rs.getLong("duration");
        if (rs.wasNull()) {
            duration = null;
        }

        long samples = rs.getLong("samples");
        long weight = rs.getLong("weight");
        String weightEntity = rs.getString("weight_entity");

        JfrThread thread = null;
        if (useThreads) {
            thread = new JfrThreadImpl(
                    rs.getLong("os_id"),
                    rs.getLong("java_id"),
                    rs.getString("name"),
                    rs.getBoolean("is_virtual"));
        }

        String eventTypeLabel = null;
        if (useEventTypeInfo) {
            eventTypeLabel = rs.getString("label");
        }

        JfrMethod weightEntityMethod = null;
        if (weightEntity != null) {
            weightEntityMethod = JfrMethodImpl.of(weightEntity);
        }

        ObjectNode jsonFields = null;
        if (useJsonFields) {
            jsonFields = (ObjectNode) Json.readTree(rs.getString("event_fields"));
        }

        return new GenericRecord(
                Type.fromCode(eventType),
                eventTypeLabel,
                timestamp,
                Duration.ofMillis(timestampFromStart),
                duration != null ? Duration.ofNanos(duration) : null,
                thread,
                weightEntityMethod,
                samples,
                weight,
                jsonFields);
    }

    public static Instant instant(ResultSet rs, String columnName) throws SQLException {
        OffsetDateTime dateTime = rs.getObject(columnName, OffsetDateTime.class);
        if (dateTime != null) {
            return dateTime.toInstant();
        }
        return null;
    }
}
