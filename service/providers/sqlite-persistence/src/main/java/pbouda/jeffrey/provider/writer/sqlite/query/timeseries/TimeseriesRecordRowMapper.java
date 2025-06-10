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

package pbouda.jeffrey.provider.writer.sqlite.query.timeseries;

import org.springframework.jdbc.core.RowMapper;
import pbouda.jeffrey.jfrparser.db.type.DbJfrStackTrace;
import pbouda.jeffrey.provider.api.streamer.model.SecondValue;
import pbouda.jeffrey.provider.api.streamer.model.TimeseriesRecord;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class TimeseriesRecordRowMapper implements RowMapper<TimeseriesRecord> {

    @Override
    public TimeseriesRecord mapRow(ResultSet r, int rn) throws SQLException {
        String[] eventValues = r.getString("event_values").split(";");

        List<SecondValue> secondValues = new ArrayList<>();
        for (String eventValue : eventValues) {
            String[] secondValue = eventValue.split(",");
            secondValues.add(new SecondValue(Long.parseLong(secondValue[0]), Long.parseLong(secondValue[1])));
        }

        DbJfrStackTrace stacktrace = new DbJfrStackTrace(
                r.getLong("stacktrace_id"),
                r.getString("frames"));

        return new TimeseriesRecord(stacktrace, secondValues);
    }
}
