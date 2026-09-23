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

import org.springframework.jdbc.core.RowMapper;
import cafe.jeffrey.jfrparser.api.type.JfrStackTraceImpl;
import cafe.jeffrey.provider.profile.api.SecondValue;
import cafe.jeffrey.provider.profile.api.TimeseriesRecord;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class TimeseriesRecordRowMapper implements RowMapper<TimeseriesRecord> {

    @Override
    public TimeseriesRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        // Read event_values as array of structs instead of parsing strings
        Array eventValuesArray = rs.getArray("event_values");
        List<SecondValue> secondValues = new ArrayList<>();

        if (eventValuesArray != null) {
            Object[] objects = (Object[]) eventValuesArray.getArray();
            secondValues = new ArrayList<>(objects.length);

            for (Object obj : objects) {
                java.sql.Struct struct = (java.sql.Struct) obj;
                Object[] attrs = struct.getAttributes();
                secondValues.add(new SecondValue(
                    ((Number) attrs[0]).longValue(),  // second
                    ((Number) attrs[1]).longValue()   // value
                ));
            }
        }

        JfrStackTraceImpl stacktrace = new JfrStackTraceImpl(
                rs.getLong("stacktrace_hash"),
                FlamegraphMapperUtils.getStackFrames(rs));

        return new TimeseriesRecord(stacktrace, secondValues);
    }
}
