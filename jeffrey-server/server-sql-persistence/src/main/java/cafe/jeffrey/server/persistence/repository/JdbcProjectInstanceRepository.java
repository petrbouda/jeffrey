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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import cafe.jeffrey.shared.common.model.ProjectInstanceInfo;
import cafe.jeffrey.shared.common.model.ProjectInstanceInfo.ProjectInstanceStatus;
import cafe.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

public class JdbcProjectInstanceRepository implements ProjectInstanceRepository {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcProjectInstanceRepository.class);

    //language=SQL
    private static final String SELECT_ALL_PROJECT_INSTANCES = """
            SELECT i.*,
                   COUNT(rs.session_id) as session_count,
                   (SELECT rs2.session_id FROM project_instance_sessions rs2
                    WHERE rs2.instance_id = i.instance_id AND rs2.finished_at IS NULL
                    ORDER BY rs2.created_at DESC LIMIT 1) as active_session_id
            FROM project_instances i
            LEFT JOIN project_instance_sessions rs ON rs.instance_id = i.instance_id
            WHERE i.project_id = :project_id
            GROUP BY i.instance_id, i.project_id, i.instance_name, i.status,
                     i.started_at, i.finished_at, i.expiring_at, i.expired_at
            ORDER BY i.started_at DESC""";

    //language=SQL
    private static final String SELECT_PROJECT_INSTANCE_BY_ID = """
            SELECT i.*,
                   COUNT(rs.session_id) as session_count,
                   (SELECT rs2.session_id FROM project_instance_sessions rs2
                    WHERE rs2.instance_id = i.instance_id AND rs2.finished_at IS NULL
                    ORDER BY rs2.created_at DESC LIMIT 1) as active_session_id
            FROM project_instances i
            LEFT JOIN project_instance_sessions rs ON rs.instance_id = i.instance_id
            WHERE i.instance_id = :instance_id
            GROUP BY i.instance_id, i.project_id, i.instance_name, i.status,
                     i.started_at, i.finished_at, i.expiring_at, i.expired_at""";

    //language=SQL
    private static final String SELECT_PROJECT_INSTANCES_BY_STATUS = """
            SELECT i.*,
                   COUNT(rs.session_id) as session_count,
                   (SELECT rs2.session_id FROM project_instance_sessions rs2
                    WHERE rs2.instance_id = i.instance_id AND rs2.finished_at IS NULL
                    ORDER BY rs2.created_at DESC LIMIT 1) as active_session_id
            FROM project_instances i
            LEFT JOIN project_instance_sessions rs ON rs.instance_id = i.instance_id
            WHERE i.project_id = :project_id AND i.status = :status
            GROUP BY i.instance_id, i.project_id, i.instance_name, i.status,
                     i.started_at, i.finished_at, i.expiring_at, i.expired_at
            ORDER BY i.started_at DESC""";

    //language=SQL
    private static final String SELECT_PROJECT_INSTANCE_SESSIONS = """
            SELECT rs.* FROM project_instance_sessions rs
            WHERE rs.instance_id = :instance_id
            ORDER BY rs.created_at DESC""";

    //language=SQL
    private static final String INSERT_PROJECT_INSTANCE = """
            INSERT INTO project_instances (instance_id, project_id, instance_name, status, started_at)
            VALUES (:instance_id, :project_id, :instance_name, :status, :started_at)""";

    //language=SQL
    private static final String UPDATE_STATUS = """
            UPDATE project_instances SET status = :status
            WHERE instance_id = :instance_id AND status IN (:valid_from_statuses)""";

    //language=SQL
    private static final String UPDATE_STATUS_AND_FINISHED_AT = """
            UPDATE project_instances SET status = :status, finished_at = :finished_at
            WHERE instance_id = :instance_id AND status IN (:valid_from_statuses)""";

    //language=SQL
    private static final String UPDATE_STATUS_AND_EXPIRED_AT = """
            UPDATE project_instances SET status = :status, expired_at = :expired_at
            WHERE instance_id = :instance_id AND status IN (:valid_from_statuses)""";

    //language=SQL
    private static final String SET_EXPIRING_AT = """
            UPDATE project_instances SET expiring_at = :expiring_at WHERE instance_id = :instance_id""";

    //language=SQL
    private static final String DELETE_INSTANCE = """
            DELETE FROM project_instances WHERE instance_id = :instance_id""";

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
                .addValue("instance_id", instanceId);

        return databaseClient.querySingle(
                StatementLabel.FIND_PROJECT_INSTANCE_BY_ID,
                SELECT_PROJECT_INSTANCE_BY_ID,
                paramSource,
                projectInstanceInfoMapper());
    }

    @Override
    public List<ProjectInstanceInfo> findByStatus(ProjectInstanceStatus status) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId)
                .addValue("status", status.name());

        return databaseClient.query(
                StatementLabel.FIND_PROJECT_INSTANCES_BY_STATUS,
                SELECT_PROJECT_INSTANCES_BY_STATUS,
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
                .addValue("instance_name", instance.instanceName())
                .addValue("status", instance.status().name())
                .addValue("started_at", instance.startedAt().atOffset(ZoneOffset.UTC));

        databaseClient.insert(StatementLabel.INSERT_PROJECT_INSTANCE, INSERT_PROJECT_INSTANCE, paramSource);
    }

    @Override
    public void updateStatus(String instanceId, ProjectInstanceStatus status) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("instance_id", instanceId)
                .addValue("status", status.name())
                .addValue("valid_from_statuses", validFromStatusNames(status));

        int updated = databaseClient.update(StatementLabel.UPDATE_PROJECT_INSTANCE_STATUS, UPDATE_STATUS, paramSource);
        logIfNoRowsUpdated(updated, instanceId, status);
    }

    @Override
    public void updateStatusAndFinishedAt(String instanceId, ProjectInstanceStatus status, Instant finishedAt) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("instance_id", instanceId)
                .addValue("status", status.name())
                .addValue("finished_at", finishedAt.atOffset(ZoneOffset.UTC))
                .addValue("valid_from_statuses", validFromStatusNames(status));

        int updated = databaseClient.update(StatementLabel.UPDATE_PROJECT_INSTANCE_STATUS_AND_FINISHED_AT, UPDATE_STATUS_AND_FINISHED_AT, paramSource);
        logIfNoRowsUpdated(updated, instanceId, status);
    }

    @Override
    public void updateStatusAndExpiredAt(String instanceId, ProjectInstanceStatus status, Instant expiredAt) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("instance_id", instanceId)
                .addValue("status", status.name())
                .addValue("expired_at", expiredAt.atOffset(ZoneOffset.UTC))
                .addValue("valid_from_statuses", validFromStatusNames(status));

        int updated = databaseClient.update(StatementLabel.UPDATE_PROJECT_INSTANCE_STATUS_AND_EXPIRED_AT, UPDATE_STATUS_AND_EXPIRED_AT, paramSource);
        logIfNoRowsUpdated(updated, instanceId, status);
    }

    private static List<String> validFromStatusNames(ProjectInstanceStatus targetStatus) {
        return targetStatus.validFromStatuses().stream()
                .map(ProjectInstanceStatus::name)
                .toList();
    }

    private static void logIfNoRowsUpdated(int updated, String instanceId, ProjectInstanceStatus targetStatus) {
        if (updated == 0) {
            LOG.warn("Instance status transition had no effect (instance not found or invalid transition): instanceId={} targetStatus={}",
                    instanceId, targetStatus);
        }
    }

    @Override
    public void setExpiringAt(String instanceId, Instant expiringAt) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("instance_id", instanceId)
                .addValue("expiring_at", expiringAt.atOffset(ZoneOffset.UTC));

        databaseClient.update(StatementLabel.SET_PROJECT_INSTANCE_EXPIRING_AT, SET_EXPIRING_AT, paramSource);
    }

    @Override
    public void delete(String instanceId) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("instance_id", instanceId);

        databaseClient.delete(StatementLabel.DELETE_PROJECT_INSTANCE, DELETE_INSTANCE, paramSource);
    }

    private static RowMapper<ProjectInstanceInfo> projectInstanceInfoMapper() {
        return (rs, _) -> {
            String statusStr = rs.getString("status");
            ProjectInstanceStatus status = ProjectInstanceStatus.valueOf(statusStr);

            return new ProjectInstanceInfo(
                    rs.getString("instance_id"),
                    rs.getString("project_id"),
                    rs.getString("instance_name"),
                    status,
                    ServerMappers.instant(rs, "started_at"),
                    ServerMappers.instant(rs, "finished_at"),
                    ServerMappers.instant(rs, "expiring_at"),
                    ServerMappers.instant(rs, "expired_at"),
                    rs.getInt("session_count"),
                    rs.getString("active_session_id"));
        };
    }

    private static RowMapper<ProjectInstanceSessionInfo> projectInstanceSessionMapper() {
        return (rs, _) -> new ProjectInstanceSessionInfo(
                rs.getString("session_id"),
                rs.getString("repository_id"),
                rs.getString("instance_id"),
                rs.getInt("session_order"),
                Path.of(rs.getString("relative_session_path")),
                ServerMappers.instant(rs, "origin_created_at"),
                ServerMappers.instant(rs, "created_at"),
                ServerMappers.instant(rs, "finished_at"));
    }
}
