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
import cafe.jeffrey.jfrparser.api.type.JfrClass;
import cafe.jeffrey.jfrparser.api.type.JfrMethodImpl;
import cafe.jeffrey.jfrparser.api.type.JfrStackFrameImpl;
import cafe.jeffrey.jfrparser.api.type.JfrStackTraceImpl;
import cafe.jeffrey.jfrparser.api.type.JfrThreadImpl;
import cafe.jeffrey.provider.profile.api.FlamegraphRecord;
import cafe.jeffrey.microscope.model.Type;

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
        List<JfrStackFrameImpl> frames = null;
        if (frameHashesArray != null) {
            long[] hashes = FlamegraphMapperUtils.toFrameHashArray(frameHashesArray);
            frames = framesCache.resolveFrames(hashes);
        }

        JfrStackTraceImpl stacktrace = new JfrStackTraceImpl(stacktraceHash, frames);
        JfrThreadImpl thread = withThreads ? FlamegraphMapperUtils.getThread(rs) : null;

        JfrClass weightEntity = null;
        if (hasWeightEntity) {
            // The column is present but its value may be NULL (e.g. OTLP/pprof allocation events carry no
            // allocated class). Keep the entity null in that case rather than wrapping null, so downstream
            // top-frame processors correctly skip the synthetic allocated/blocking-object leaf.
            String weightEntityName = rs.getString("weight_entity");
            if (weightEntityName != null) {
                weightEntity = JfrMethodImpl.ofClass(weightEntityName);
            }
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
