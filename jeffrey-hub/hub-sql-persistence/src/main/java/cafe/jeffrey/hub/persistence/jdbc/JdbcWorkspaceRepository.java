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

import cafe.jeffrey.hub.persistence.api.WorkspaceRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import cafe.jeffrey.hub.model.ProjectInfo;
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
            "DELETE FROM projects WHERE workspace_id = :workspace_id",
            "DELETE FROM workspaces WHERE workspace_id = :workspace_id");

    //language=SQL
    private static final String SELECT_PROJECTS_BY_WORKSPACE_ID = """
            SELECT p.* FROM projects p
            WHERE EXISTS (SELECT 1 FROM workspaces w WHERE w.workspace_id = p.workspace_id)
              AND p.workspace_id = :workspace_id AND p.deleted_at IS NULL""";

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
