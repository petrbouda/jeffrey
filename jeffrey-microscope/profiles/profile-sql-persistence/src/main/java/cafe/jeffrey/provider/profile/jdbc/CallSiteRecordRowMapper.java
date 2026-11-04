/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

import cafe.jeffrey.jfrparser.api.type.JfrStackFrameImpl;
import cafe.jeffrey.jfrparser.api.type.JfrStackTraceImpl;
import cafe.jeffrey.provider.profile.api.CallSiteRecord;
import cafe.jeffrey.provider.profile.api.TraceOperationId;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * One row of {@link DuckDBCallSiteQueries}, its stack resolved through the profile's frame cache the way
 * the flamegraph's own rows are.
 */
record CallSiteRecordRowMapper(FramesCache framesCache) implements RowMapper<CallSiteRecord> {

    private static final String STACKTRACE_HASH = "stacktrace_hash";
    private static final String FRAME_HASHES = "frame_hashes";
    private static final String GROUP_VALUE = "group_value";
    private static final String TRACE_ID = "trace_id";
    private static final String ROOT_NAME = "root_name";
    private static final String ROOT_KIND = "root_kind";
    private static final String ROOT_EVENT_TYPE = "root_event_type";
    private static final String EVENT_COUNT = "event_count";
    private static final String TOTAL_DURATION = "total_duration";
    private static final String MAX_DURATION = "max_duration";

    @Override
    public CallSiteRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        Array frameHashes = rs.getArray(FRAME_HASHES);
        List<JfrStackFrameImpl> frames = frameHashes == null
                ? List.of()
                : framesCache.resolveFrames(FlamegraphMapperUtils.toFrameHashArray(frameHashes));
        long traceId = rs.getLong(TRACE_ID);
        Long trace = rs.wasNull() ? null : traceId;
        String rootName = rs.getString(ROOT_NAME);
        TraceOperationId operation = rootName == null
                ? null
                : new TraceOperationId(rootName, rs.getString(ROOT_KIND), rs.getString(ROOT_EVENT_TYPE));
        return new CallSiteRecord(
                new JfrStackTraceImpl(rs.getLong(STACKTRACE_HASH), frames),
                rs.getString(GROUP_VALUE),
                trace,
                operation,
                rs.getLong(EVENT_COUNT),
                rs.getLong(TOTAL_DURATION),
                rs.getLong(MAX_DURATION));
    }
}
