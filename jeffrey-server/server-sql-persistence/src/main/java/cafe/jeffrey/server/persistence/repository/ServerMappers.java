/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.server.persistence.repository;

import org.springframework.jdbc.core.RowMapper;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.shared.common.model.RepositoryInfo;
import cafe.jeffrey.shared.common.model.RepositoryType;
import cafe.jeffrey.shared.common.model.job.JobInfo;
import cafe.jeffrey.shared.common.model.job.JobType;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;

public abstract class ServerMappers {

    public static Instant instant(ResultSet rs, String columnName) throws SQLException {
        OffsetDateTime dateTime = rs.getObject(columnName, OffsetDateTime.class);
        if (dateTime != null) {
            return dateTime.toInstant();
        }
        return null;
    }

    public static Instant safeParseTimestamp(long timestamp) {
        return timestamp == 0 ? null : Instant.ofEpochMilli(timestamp);
    }

    public static RowMapper<ProfileInfo> profileInfoMapper() {
        return (rs, _) -> {
            return new ProfileInfo(
                    rs.getString("profile_id"),
                    rs.getString("project_id"),
                    null,
                    rs.getString("profile_name"),
                    RecordingEventSource.valueOf(rs.getString("event_source")),
                    ServerMappers.instant(rs, "recording_started_at"),
                    ServerMappers.instant(rs, "recording_finished_at"),
                    ServerMappers.instant(rs, "created_at"),
                    safeParseTimestamp(rs.getLong("enabled_at")) != null,
                    false,
                    null);
        };
    }

    public static RowMapper<JobInfo> jobInfoMapper() {
        return (rs, _) -> {
            String id = rs.getString("id");
            String projectId = rs.getString("project_id");
            String jobType = rs.getString("job_type");
            String params = rs.getString("params");
            boolean enabled = rs.getBoolean("enabled");
            return new JobInfo(id, projectId, JobType.valueOf(jobType), Json.toMap(params), enabled);
        };
    }

    public static RowMapper<RepositoryInfo> repositoryInfoMapper() {
        return (rs, _) -> {
            return new RepositoryInfo(
                    rs.getString("repository_id"),
                    RepositoryType.valueOf(rs.getString("repository_type")),
                    rs.getString("workspaces_path"),
                    rs.getString("relative_workspace_path"),
                    rs.getString("relative_project_path"));
        };
    }

    public static RowMapper<ProjectInstanceSessionInfo> projectInstanceSessionMapper() {
        return (rs, _) -> {
            return new ProjectInstanceSessionInfo(
                    rs.getString("session_id"),
                    rs.getString("repository_id"),
                    rs.getString("instance_id"),
                    rs.getInt("session_order"),
                    Path.of(rs.getString("relative_session_path")),
                    ServerMappers.instant(rs, "origin_created_at"),
                    ServerMappers.instant(rs, "created_at"),
                    ServerMappers.instant(rs, "finished_at")
            );
        };
    }

    public static RowMapper<ProjectInfo> projectInfoMapper() {
        return (rs, _) -> {
            return new ProjectInfo(
                    rs.getString("project_id"),
                    rs.getString("origin_project_id"),
                    rs.getString("project_name"),
                    rs.getString("project_label"),
                    rs.getString("namespace"),
                    rs.getString("workspace_id"),
                    ServerMappers.instant(rs, "created_at"),
                    ServerMappers.instant(rs, "origin_created_at"),
                    Json.toMap(rs.getString("attributes")),
                    ServerMappers.instant(rs, "deleted_at"));
        };
    }
}
