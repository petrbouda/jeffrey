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

package pbouda.jeffrey.provider.writer.sqlite.query;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.RowMapper;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.jfrparser.api.record.GenericRecord;
import pbouda.jeffrey.jfrparser.api.record.RecordEventType;
import pbouda.jeffrey.jfrparser.api.type.JfrClass;
import pbouda.jeffrey.jfrparser.api.type.JfrEventType;
import pbouda.jeffrey.jfrparser.api.type.JfrStackTrace;
import pbouda.jeffrey.jfrparser.api.type.JfrThread;
import pbouda.jeffrey.jfrparser.db.type.DbJfrMethod;
import pbouda.jeffrey.jfrparser.db.type.DbJfrStackTrace;
import pbouda.jeffrey.jfrparser.db.type.DbJfrThread;
import pbouda.jeffrey.provider.api.streamer.EventStreamConfigurer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;

public class GenericRecordRowMapper implements RowMapper<GenericRecord> {

    private final boolean useThreads;
    private final boolean useEventTypeInfo;
    private final boolean useStackTraces;
    private final boolean useJsonFields;

    public GenericRecordRowMapper(EventStreamConfigurer configurer) {
        this.useThreads = configurer.threads();
        this.useEventTypeInfo = configurer.eventTypeInfo();
        this.useStackTraces = configurer.includeFrames();
        this.useJsonFields = configurer.jsonFields();
    }

    @Override
    public GenericRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        String eventType = rs.getString("event_type");
        long timestamp = rs.getLong("timestamp");
        long samples = rs.getLong("samples");
        long weight = rs.getLong("weight");
        String weightEntity = rs.getString("weight_entity");

        JfrThread thread = null;
        if (useThreads) {
            thread = new DbJfrThread(
                    rs.getLong("os_id"),
                    rs.getLong("java_id"),
                    rs.getString("name"));
        }

        JfrEventType jfrEventType = null;
        if (useEventTypeInfo) {
            jfrEventType = new RecordEventType(eventType, rs.getString("label"));
        }

        JfrStackTrace stackTrace = null;
        if (useStackTraces) {
            long stacktraceId = rs.getLong("stacktrace_id");
            String frames = rs.getString("frames");
            stackTrace = new DbJfrStackTrace(stacktraceId, frames);
        }

        JfrClass weightEntityClass = null;
        if (weightEntity != null) {
            weightEntityClass = DbJfrMethod.ofClass(weightEntity);
        }

        ObjectNode jsonFields = null;
        if (useJsonFields) {
            jsonFields = (ObjectNode) Json.readTree(rs.getString("fields"));
        }

        return new GenericRecord(
                Type.fromCode(eventType),
                Instant.ofEpochMilli(timestamp),
                jfrEventType,
                stackTrace,
                thread,
                weightEntityClass,
                samples,
                weight,
                jsonFields);
    }
}
