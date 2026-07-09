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

package cafe.jeffrey.hub.persistence.jdbc;

import cafe.jeffrey.hub.persistence.api.WorkspaceRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.util.List;

public class JdbcWorkspaceRepository implements WorkspaceRepository {

    /**
     * Cascade covering all rows reachable only through the workspace. Projects (including soft-deleted
     * ones) and their children must go too — once the workspace row is gone, every project query
     * (INNER JOIN workspaces) can no longer see them, so leftover rows would be unreachable garbage.
     */
    //language=SQL
    private static final List<String> DELETE_WORKSPACE_CASCADE = List.of(
            """
            DELETE FROM project_instance_sessions WHERE repository_id IN (
                SELECT r.repository_id FROM repositories r
                JOIN projects p ON r.project_id = p.project_id
                WHERE p.workspace_id = :workspace_id)""",
            "DELETE FROM project_instances WHERE project_id IN (SELECT project_id FROM projects WHERE workspace_id = :workspace_id)",
            "DELETE FROM repositories WHERE project_id IN (SELECT project_id FROM projects WHERE workspace_id = :workspace_id)",
            "DELETE FROM profiler_settings WHERE project_id IN (SELECT project_id FROM projects WHERE workspace_id = :workspace_id)",
            "DELETE FROM profiler_settings WHERE workspace_id = :workspace_id AND project_id IS NULL",
            "DELETE FROM persistent_queue_consumers WHERE scope_id = :workspace_id",
            "DELETE FROM persistent_queue_events WHERE scope_id = :workspace_id",
            "DELETE FROM projects WHERE workspace_id = :workspace_id",
            "DELETE FROM workspaces WHERE workspace_id = :workspace_id");

    //language=SQL
    private static final String SELECT_PROJECTS_BY_WORKSPACE_ID = """
            SELECT * FROM projects p
            JOIN workspaces w ON p.workspace_id = w.workspace_id
            WHERE w.workspace_id = :workspace_id AND p.deleted_at IS NULL""";

    private final String workspaceId;
    private final DatabaseClient databaseClient;

    public JdbcWorkspaceRepository(String workspaceId, DatabaseClientProvider databaseClientProvider) {
        this.workspaceId = workspaceId;
        this.databaseClient = databaseClientProvider.provide(GroupLabel.WORKSPACES);
    }

    @Override
    public void delete() {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("workspace_id", workspaceId);

        databaseClient.deleteCascade(StatementLabel.DELETE_WORKSPACE, DELETE_WORKSPACE_CASCADE, paramSource);
    }

    @Override
    public List<ProjectInfo> findAllProjects() {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("workspace_id", this.workspaceId);

        return databaseClient.query(
                StatementLabel.FIND_PROJECTS_BY_WORKSPACE_ID,
                SELECT_PROJECTS_BY_WORKSPACE_ID,
                paramSource,
                HubMappers.projectInfoMapper());
    }
}
