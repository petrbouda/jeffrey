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

import cafe.jeffrey.provider.profile.api.*;

import org.springframework.jdbc.core.RowMapper;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.jfrparser.api.type.JfrMethodImpl;
import cafe.jeffrey.jfrparser.api.type.JfrStackTraceImpl;
import cafe.jeffrey.jfrparser.api.type.JfrThreadImpl;
import cafe.jeffrey.provider.profile.api.FlamegraphRecord;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Row mapper over queries with fully resolved frames. When {@code withThreads} is enabled,
 * the thread information is extracted from the row as well.
 */
public record FlamegraphRecordRowMapper(Type eventType, boolean withThreads) implements RowMapper<FlamegraphRecord> {

    @Override
    public FlamegraphRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        JfrStackTraceImpl stacktrace = new JfrStackTraceImpl(
                rs.getLong("stacktrace_hash"), FlamegraphMapperUtils.getStackFrames(rs));

        JfrThreadImpl thread = withThreads ? FlamegraphMapperUtils.getThread(rs) : null;

        return new FlamegraphRecord(
                eventType,
                stacktrace,
                thread,
                JfrMethodImpl.ofClass(rs.getString("weight_entity")),
                rs.getLong("total_samples"),
                rs.getLong("total_weight")
        );
    }
}
