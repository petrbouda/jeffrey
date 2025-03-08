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

package pbouda.jeffrey.provider.writer.sqlite.repository;

import org.springframework.jdbc.core.RowMapper;
import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.common.model.profile.ProfileInfo;

import java.time.Instant;

public abstract class Mappers {

    static RowMapper<ProfileInfo> profileInfoMapper() {
        return (rs, rowNum) -> {
            return new ProfileInfo(
                    rs.getString("profile_id"),
                    rs.getString("project_id"),
                    rs.getString("profile_name"),
                    Instant.ofEpochMilli(rs.getLong("profiling_started_at")),
                    Instant.ofEpochMilli(rs.getLong("profiling_finished_at")),
                    Instant.ofEpochMilli(rs.getLong("created_at")),
                    safeParseTimestamp(rs.getLong("enabled_at")) != null);
        };
    }

    static RowMapper<ProjectInfo> projectInfoMapper() {
        return (rs, rowNum) -> {
            return new ProjectInfo(
                    rs.getString("project_id"),
                    rs.getString("project_name"),
                    Instant.ofEpochMilli(rs.getLong("created_at")));
        };
    }

    static Instant safeParseTimestamp(long timestamp) {
        return timestamp == 0 ? Instant.ofEpochMilli(timestamp) : null;
    }
}
