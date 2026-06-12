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

package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.provider.profile.api.*;

import org.springframework.jdbc.core.RowMapper;
import cafe.jeffrey.jfrparser.api.type.JfrClass;
import cafe.jeffrey.jfrparser.db.type.DbJfrMethod;
import cafe.jeffrey.jfrparser.db.type.DbJfrStackFrame;
import cafe.jeffrey.jfrparser.db.type.DbJfrStackTrace;
import cafe.jeffrey.jfrparser.db.type.DbJfrThread;
import cafe.jeffrey.provider.profile.api.FlamegraphRecord;
import cafe.jeffrey.shared.common.model.Type;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Optimized row mapper that resolves frames using a pre-loaded cache.
 * Works with the *_OPTIMIZED queries that return frame_hashes instead of resolved frames.
 * When {@code withThreads} is enabled, the thread information is extracted from the row as well
 * (BY_THREAD_OPTIMIZED and BY_THREAD_AND_WEIGHT_OPTIMIZED queries).
 */
public record CachingFlamegraphRecordRowMapper(
        Type eventType,
        FramesCache framesCache,
        boolean hasWeightEntity,
        boolean withThreads
) implements RowMapper<FlamegraphRecord> {

    @Override
    public FlamegraphRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        long stacktraceHash = rs.getLong("stacktrace_hash");

        // Get frame_hashes array and resolve using cache
        Array frameHashesArray = rs.getArray("frame_hashes");
        List<DbJfrStackFrame> frames = null;
        if (frameHashesArray != null) {
            long[] hashes = FlamegraphMapperUtils.toFrameHashArray(frameHashesArray);
            frames = framesCache.resolveFrames(hashes);
        }

        DbJfrStackTrace stacktrace = new DbJfrStackTrace(stacktraceHash, frames);
        DbJfrThread thread = withThreads ? FlamegraphMapperUtils.getThread(rs) : null;

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
