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

package pbouda.jeffrey.provider.platform.repository;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import pbouda.jeffrey.shared.common.model.ProjectInstanceInfo;
import pbouda.jeffrey.shared.common.model.ProjectInstanceInfo.ProjectInstanceStatus;
import pbouda.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import pbouda.jeffrey.shared.persistence.GroupLabel;
import pbouda.jeffrey.shared.persistence.StatementLabel;
import pbouda.jeffrey.shared.persistence.client.DatabaseClient;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

public class JdbcProjectInstanceRepository implements ProjectInstanceRepository {

    //language=SQL
    private static final String SELECT_ALL_PROJECT_INSTANCES = """
            SELECT i.*,
                   (SELECT COUNT(*) FROM project_instance_sessions rs WHERE rs.instance_id = i.instance_id) as session_count,
                   (SELECT rs.session_id FROM project_instance_sessions rs
                    WHERE rs.instance_id = i.instance_id AND rs.finished_at IS NULL
                    ORDER BY rs.created_at DESC LIMIT 1) as active_session_id,
                   (SELECT MAX(rs.finished_at) FROM project_instance_sessions rs
                    WHERE rs.instance_id = i.instance_id) as last_session_finished_at
            FROM project_instances i
            WHERE i.project_id = :project_id
            ORDER BY i.started_at DESC""";

    //language=SQL
    private static final String SELECT_PROJECT_INSTANCE_BY_ID = """
            SELECT i.*,
                   (SELECT COUNT(*) FROM project_instance_sessions rs WHERE rs.instance_id = i.instance_id) as session_count,
                   (SELECT rs.session_id FROM project_instance_sessions rs
                    WHERE rs.instance_id = i.instance_id AND rs.finished_at IS NULL
                    ORDER BY rs.created_at DESC LIMIT 1) as active_session_id,
                   (SELECT MAX(rs.finished_at) FROM project_instance_sessions rs
                    WHERE rs.instance_id = i.instance_id) as last_session_finished_at
            FROM project_instances i
            WHERE i.instance_id = :instance_id AND i.project_id = :project_id""";

    //language=SQL
    private static final String SELECT_PROJECT_INSTANCE_SESSIONS = """
            SELECT rs.* FROM project_instance_sessions rs
            WHERE rs.instance_id = :instance_id
            ORDER BY rs.created_at DESC""";

    //language=SQL
    private static final String INSERT_PROJECT_INSTANCE = """
            INSERT INTO project_instances (instance_id, project_id, hostname, started_at)
            VALUES (:instance_id, :project_id, :hostname, :started_at)""";

    private final String projectId;
    private final DatabaseClient databaseClient;

    public JdbcProjectInstanceRepository(String projectId, DatabaseClientProvider databaseClientProvider) {
        this.projectId = projectId;
        this.databaseClient = databaseClientProvider.provide(GroupLabel.PROJECT_INSTANCES);
    }

    @Override
    public List<ProjectInstanceInfo> findAll() {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId);

        return databaseClient.query(
                StatementLabel.FIND_ALL_PROJECT_INSTANCES,
                SELECT_ALL_PROJECT_INSTANCES,
                paramSource,
                projectInstanceInfoMapper());
    }

    @Override
    public Optional<ProjectInstanceInfo> find(String instanceId) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId)
                .addValue("instance_id", instanceId);

        return databaseClient.querySingle(
                StatementLabel.FIND_PROJECT_INSTANCE_BY_ID,
                SELECT_PROJECT_INSTANCE_BY_ID,
                paramSource,
                projectInstanceInfoMapper());
    }

    @Override
    public List<ProjectInstanceSessionInfo> findSessions(String instanceId) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("instance_id", instanceId);

        return databaseClient.query(
                StatementLabel.FIND_PROJECT_INSTANCE_SESSIONS,
                SELECT_PROJECT_INSTANCE_SESSIONS,
                paramSource,
                projectInstanceSessionMapper());
    }

    @Override
    public void insert(ProjectInstanceInfo instance) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("instance_id", instance.id())
                .addValue("project_id", projectId)
                .addValue("hostname", instance.hostname())
                .addValue("started_at", instance.startedAt().atOffset(ZoneOffset.UTC));

        databaseClient.insert(StatementLabel.INSERT_PROJECT_INSTANCE, INSERT_PROJECT_INSTANCE, paramSource);
    }

    private static RowMapper<ProjectInstanceInfo> projectInstanceInfoMapper() {
        return (rs, _) -> {
            String activeSessionId = rs.getString("active_session_id");
            int sessionCount = rs.getInt("session_count");
            Instant lastSessionFinishedAt = Mappers.instant(rs, "last_session_finished_at");

            ProjectInstanceStatus status;
            Instant finishedAt;
            if (activeSessionId != null) {
                status = ProjectInstanceStatus.ACTIVE;
                finishedAt = null;
            } else if (sessionCount == 0) {
                status = ProjectInstanceStatus.PENDING;
                finishedAt = null;
            } else {
                status = ProjectInstanceStatus.FINISHED;
                finishedAt = lastSessionFinishedAt;
            }

            return new ProjectInstanceInfo(
                    rs.getString("instance_id"),
                    rs.getString("project_id"),
                    rs.getString("hostname"),
                    status,
                    Mappers.instant(rs, "started_at"),
                    finishedAt,
                    sessionCount,
                    activeSessionId);
        };
    }

    private static RowMapper<ProjectInstanceSessionInfo> projectInstanceSessionMapper() {
        return (rs, _) -> new ProjectInstanceSessionInfo(
                rs.getString("session_id"),
                rs.getString("repository_id"),
                rs.getString("instance_id"),
                rs.getInt("session_order"),
                Path.of(rs.getString("relative_session_path")),
                rs.getString("profiler_settings"),
                rs.getBoolean("streaming_enabled"),
                Mappers.instant(rs, "origin_created_at"),
                Mappers.instant(rs, "created_at"),
                Mappers.instant(rs, "finished_at"),
                Mappers.instant(rs, "last_heartbeat_at"));
    }
}
