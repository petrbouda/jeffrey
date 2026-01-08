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

package pbouda.jeffrey.provider.profile.query;

import org.springframework.jdbc.core.RowMapper;
import pbouda.jeffrey.jfrparser.api.type.JfrClass;
import pbouda.jeffrey.jfrparser.db.type.DbJfrMethod;
import pbouda.jeffrey.jfrparser.db.type.DbJfrStackFrame;
import pbouda.jeffrey.jfrparser.db.type.DbJfrStackTrace;
import pbouda.jeffrey.jfrparser.db.type.DbJfrThread;
import pbouda.jeffrey.provider.profile.model.FlamegraphRecord;
import pbouda.jeffrey.shared.common.model.Type;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Optimized row mapper that resolves frames using a pre-loaded cache and also extracts thread information.
 * Works with BY_THREAD_OPTIMIZED and BY_THREAD_AND_WEIGHT_OPTIMIZED queries that return frame_hashes instead of resolved frames.
 */
public record CachingFlamegraphRecordWithThreadsRowMapper(
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
            long[] hashes = toFrameHashArray(frameHashesArray);
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

    /**
     * Converts DuckDB array to primitive long array.
     * DuckDB JDBC may return different array types depending on context.
     */
    private static long[] toFrameHashArray(Array frameHashesArray) throws SQLException {
        Object arrayObj = frameHashesArray.getArray();
        return switch (arrayObj) {
            case long[] primitiveArray -> primitiveArray;
            case Long[] longArray -> {
                long[] result = new long[longArray.length];
                for (int i = 0; i < longArray.length; i++) {
                    result[i] = longArray[i];
                }
                yield result;
            }
            case Object[] objectArray -> {
                // DuckDB may return Object[] with Long/Number elements
                long[] result = new long[objectArray.length];
                for (int i = 0; i < objectArray.length; i++) {
                    result[i] = ((Number) objectArray[i]).longValue();
                }
                yield result;
            }
            default -> throw new SQLException("Unexpected array type: " + arrayObj.getClass());
        };
    }
}
