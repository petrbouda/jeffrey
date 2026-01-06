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
import pbouda.jeffrey.jfrparser.api.type.JfrClass;
import pbouda.jeffrey.jfrparser.db.type.DbJfrMethod;
import pbouda.jeffrey.jfrparser.db.type.DbJfrStackFrame;
import pbouda.jeffrey.jfrparser.db.type.DbJfrStackTrace;
import pbouda.jeffrey.jfrparser.db.type.DbJfrThread;
import pbouda.jeffrey.jmh.flamegraph.utils.FramesCache;
import pbouda.jeffrey.provider.profile.model.FlamegraphRecord;
import pbouda.jeffrey.provider.profile.query.FlamegraphMapperUtils;
import pbouda.jeffrey.shared.common.model.Type;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Optimized row mapper that resolves frames using a pre-loaded cache and also extracts thread information.
 * Works with BY_THREAD_OPTIMIZED and BY_THREAD_AND_WEIGHT_OPTIMIZED queries that return frame_hashes instead of resolved frames.
 */
public record OptimizedFlamegraphRecordWithThreadsRowMapper(
        Type eventType,
        FramesCache framesCache,
        boolean hasWeightEntity
) implements RowMapper<FlamegraphRecord> {

    @Override
    public FlamegraphRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        long stacktraceHash = rs.getLong("stacktrace_hash");

        // Get frame_hashes array and resolve using cache
        Array frameHashesArray = rs.getArray("frame_hashes");
        List<DbJfrStackFrame> frames = null;
        if (frameHashesArray != null) {
            Object arrayObj = frameHashesArray.getArray();
            Long[] hashes;
            switch (arrayObj) {
                case long[] primitiveArray -> {
                    hashes = new Long[primitiveArray.length];
                    for (int i = 0; i < primitiveArray.length; i++) {
                        hashes[i] = primitiveArray[i];
                    }
                }
                case Long[] longArray -> hashes = longArray;
                case Object[] objectArray -> {
                    hashes = new Long[objectArray.length];
                    for (int i = 0; i < objectArray.length; i++) {
                        hashes[i] = ((Number) objectArray[i]).longValue();
                    }
                }
                default -> throw new SQLException("Unexpected array type: " + arrayObj.getClass());
            }
            frames = framesCache.resolveFrames(hashes);
        }

        DbJfrStackTrace stacktrace = new DbJfrStackTrace(stacktraceHash, frames);
        DbJfrThread thread = FlamegraphMapperUtils.getThread(rs);

        JfrClass weightEntity = null;
        if (hasWeightEntity) {
            weightEntity = DbJfrMethod.ofClass(rs.getString("weight_entity"));
        }

        return new FlamegraphRecord(
                eventType,
                stacktrace,
                thread,
                weightEntity,
                rs.getLong("total_samples"),
                rs.getLong("total_weight")
        );
    }
}
