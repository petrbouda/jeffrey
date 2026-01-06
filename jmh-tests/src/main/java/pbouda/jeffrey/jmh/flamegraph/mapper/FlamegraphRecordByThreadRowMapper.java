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

package pbouda.jeffrey.jmh.flamegraph.mapper;

import org.springframework.jdbc.core.RowMapper;
import pbouda.jeffrey.jfrparser.db.type.DbJfrStackTrace;
import pbouda.jeffrey.provider.profile.model.FlamegraphRecord;
import pbouda.jeffrey.provider.profile.query.FlamegraphMapperUtils;
import pbouda.jeffrey.shared.common.model.Type;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Row mapper for byThread queries that include thread information but NOT weight_entity.
 */
public record FlamegraphRecordByThreadRowMapper(Type eventType) implements RowMapper<FlamegraphRecord> {

    @Override
    public FlamegraphRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        DbJfrStackTrace stacktrace = new DbJfrStackTrace(
                rs.getLong("stacktrace_hash"), FlamegraphMapperUtils.getStackFrames(rs));

        return new FlamegraphRecord(
                eventType,
                stacktrace,
                FlamegraphMapperUtils.getThread(rs),
                null,  // byThread query doesn't include weight_entity
                rs.getLong("total_samples"),
                rs.getLong("total_weight")
        );
    }
}
