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

package cafe.jeffrey.hub.persistence.jdbc;

import org.springframework.jdbc.core.RowMapper;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.hub.model.ProjectInstanceInfo;
import cafe.jeffrey.hub.model.ProjectInstanceInfo.ProjectInstanceStatus;
import cafe.jeffrey.hub.model.ProjectInstanceSessionInfo;
import cafe.jeffrey.hub.model.RepositoryInfo;
import cafe.jeffrey.shared.common.model.RepositoryType;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;

public abstract class HubMappers {

    public static Instant instant(ResultSet rs, String columnName) throws SQLException {
        OffsetDateTime dateTime = rs.getObject(columnName, OffsetDateTime.class);
        if (dateTime != null) {
            return dateTime.toInstant();
        }
        return null;
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

    /**
     * The instance row as every instance query selects it: the columns of {@code project_instances}
     * plus the two the query derives, {@code session_count} and {@code active_session_id}.
     * {@link #INSTANCE_WITH_COUNTS} is the SELECT that produces them; a query composes it with
     * its own WHERE and ends with {@link #GROUP_BY_INSTANCE}.
     */
    //language=SQL
    public static final String INSTANCE_WITH_COUNTS = """
            SELECT i.*,
                   COUNT(rs.session_id) as session_count,
                   (SELECT rs2.session_id FROM project_instance_sessions rs2
                    WHERE rs2.instance_id = i.instance_id AND rs2.finished_at IS NULL
                    ORDER BY rs2.created_at DESC LIMIT 1) as active_session_id
            FROM project_instances i
            LEFT JOIN project_instance_sessions rs ON rs.instance_id = i.instance_id
            """;

    //language=SQL
    public static final String GROUP_BY_INSTANCE = """
            GROUP BY i.instance_id, i.project_id, i.instance_name, i.status,
                     i.started_at, i.finished_at, i.expiring_at, i.expired_at
            """;

    public static RowMapper<ProjectInstanceInfo> projectInstanceMapper() {
        return (rs, _) -> new ProjectInstanceInfo(
                rs.getString("instance_id"),
                rs.getString("project_id"),
                rs.getString("instance_name"),
                ProjectInstanceStatus.valueOf(rs.getString("status")),
                instant(rs, "started_at"),
                instant(rs, "finished_at"),
                instant(rs, "expiring_at"),
                instant(rs, "expired_at"),
                rs.getInt("session_count"),
                rs.getString("active_session_id"));
    }

    public static RowMapper<ProjectInstanceSessionInfo> projectInstanceSessionMapper() {
        return (rs, _) -> {
            return new ProjectInstanceSessionInfo(
                    rs.getString("session_id"),
                    rs.getString("repository_id"),
                    rs.getString("instance_id"),
                    rs.getInt("session_order"),
                    Path.of(rs.getString("relative_session_path")),
                    HubMappers.instant(rs, "origin_created_at"),
                    HubMappers.instant(rs, "created_at"),
                    HubMappers.instant(rs, "finished_at"),
                    rs.getBoolean("retained"),
                    rs.getObject("heartbeat_expected", Boolean.class)
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
                    HubMappers.instant(rs, "created_at"),
                    HubMappers.instant(rs, "origin_created_at"),
                    Json.toMap(rs.getString("attributes")),
                    HubMappers.instant(rs, "deleted_at"));
        };
    }
}
