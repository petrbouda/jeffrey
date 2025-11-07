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

package pbouda.jeffrey.provider.writer.sql.query.timeseries;

import org.springframework.jdbc.core.RowMapper;
import pbouda.jeffrey.jfrparser.db.type.DbJfrStackTrace;
import pbouda.jeffrey.provider.api.repository.model.SecondValue;
import pbouda.jeffrey.provider.api.repository.model.TimeseriesRecord;
import pbouda.jeffrey.provider.writer.sql.query.FlamegraphMapperUtils;

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

        DbJfrStackTrace stacktrace = new DbJfrStackTrace(
                rs.getLong("stacktrace_hash"),
                FlamegraphMapperUtils.getStackFrames(rs));

        return new TimeseriesRecord(stacktrace, secondValues);
    }
}
