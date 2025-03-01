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

package pbouda.jeffrey.provider.writer.sqlite.repository;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.RowMapper;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.jfrparser.api.record.RecordEventType;
import pbouda.jeffrey.jfrparser.api.record.SimpleRecord;
import pbouda.jeffrey.jfrparser.api.type.JfrClass;
import pbouda.jeffrey.jfrparser.api.type.JfrEventType;
import pbouda.jeffrey.jfrparser.api.type.JfrStackTrace;
import pbouda.jeffrey.jfrparser.api.type.JfrThread;
import pbouda.jeffrey.jfrparser.db.type.DbJfrMethod;
import pbouda.jeffrey.jfrparser.db.type.DbJfrStackTrace;
import pbouda.jeffrey.jfrparser.db.type.DbJfrThread;
import pbouda.jeffrey.provider.api.repository.RecordQuery;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class SimpleRecordRowMapper implements RowMapper<SimpleRecord> {

    private final RecordQuery recordQuery;

    public SimpleRecordRowMapper(RecordQuery recordQuery) {
        this.recordQuery = recordQuery;
    }

    @Override
    public SimpleRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        List<String> columnNames = columnNames(rs);

        String eventName = rs.getString("event_name");
        long timestamp = rs.getLong("timestamp");
        long timestampFromStart = rs.getLong("timestamp_from_start");
        long samples = rs.getLong("samples");
        long weight = rs.getLong("weight");
        String weightEntity = rs.getString("weight_entity");

        JfrThread thread = null;
        if (recordQuery.withThreads()) {
            thread = new DbJfrThread(
                    rs.getLong("os_id"),
                    rs.getLong("java_id"),
                    rs.getString("name"));
        }

        JfrEventType eventType = null;
        if (recordQuery.withEventTypesInfo()) {
            eventType = new RecordEventType(eventName, rs.getString("label"));
        }

        JfrStackTrace stackTrace = null;
        if (recordQuery.withStacktraces()) {
            long stacktraceId = rs.getLong("stacktrace_id");
            String frames = rs.getString("frames");
            stackTrace = new DbJfrStackTrace(stacktraceId, frames);
        }

        JfrClass weightEntityClass = null;
        if (weightEntity != null) {
            weightEntityClass = DbJfrMethod.ofClass(weightEntity);
        }

        ObjectNode jsonFields = null;
        if (columnNames.contains("fields")) {
            jsonFields = (ObjectNode) Json.readTree(rs.getString("fields"));
        }

        return new SimpleRecord(
                Type.fromCode(eventName),
                Instant.ofEpochMilli(timestamp),
                Duration.ofMillis(timestampFromStart),
                eventType,
                stackTrace,
                thread,
                weightEntityClass,
                samples,
                weight,
                jsonFields);
    }

    private static List<String> columnNames(ResultSet rs) throws SQLException {
        ResultSetMetaData metadata = rs.getMetaData();
        int columnCount = metadata.getColumnCount();
        List<String> columnNames = new ArrayList<>();
        for (int i = 1; i <= columnCount; i++) {
            columnNames.add(metadata.getColumnName(i));
        }
        return columnNames;
    }
}
