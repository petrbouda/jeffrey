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

import org.springframework.jdbc.core.RowMapper;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.jfrparser.api.type.JfrThread;
import pbouda.jeffrey.jfrparser.db.type.DbJfrMethod;
import pbouda.jeffrey.jfrparser.db.type.DbJfrStackTrace;
import pbouda.jeffrey.jfrparser.db.type.DbJfrThread;
import pbouda.jeffrey.provider.api.repository.EventQueryConfigurer;
import pbouda.jeffrey.provider.api.streamer.model.FlamegraphRecord;

import java.sql.ResultSet;
import java.sql.SQLException;

public class FlamegraphRecordRowMapper implements RowMapper<FlamegraphRecord> {

    private final Type eventType;
    private final boolean useThreads;
    private final boolean useWeight;

    public FlamegraphRecordRowMapper(EventQueryConfigurer configurer) {
        this.eventType = configurer.eventTypes().getFirst();
        this.useThreads = configurer.threads();
        this.useWeight = configurer.useWeight();
    }

    @Override
    public FlamegraphRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
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

        return new FlamegraphRecord(
                eventType,
                new DbJfrStackTrace(rs.getLong("stacktrace_id"), rs.getString("frames")),
                thread,
                weightEntity != null ? DbJfrMethod.ofClass(weightEntity) : null,
                samples,
                weight);
    }
}
