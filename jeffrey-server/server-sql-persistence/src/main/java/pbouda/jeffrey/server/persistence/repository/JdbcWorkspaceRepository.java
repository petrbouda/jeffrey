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

package pbouda.jeffrey.server.persistence.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.persistence.GroupLabel;
import pbouda.jeffrey.shared.persistence.StatementLabel;
import pbouda.jeffrey.shared.persistence.client.DatabaseClient;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.util.List;

public class JdbcWorkspaceRepository implements WorkspaceRepository {

    //language=SQL
    private static final String DELETE_WORKSPACE = """
            DELETE FROM profiler_settings WHERE workspace_id = '%workspace_id%' AND project_id IS NULL;
            DELETE FROM persistent_queue_consumers WHERE scope_id = '%workspace_id%';
            DELETE FROM persistent_queue_events WHERE scope_id = '%workspace_id%';
            DELETE FROM workspaces WHERE workspace_id = '%workspace_id%'""";

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
        String sql = DELETE_WORKSPACE.replaceAll("%workspace_id%", workspaceId);
        databaseClient.delete(StatementLabel.DELETE_WORKSPACE, sql);
    }

    @Override
    public List<ProjectInfo> findAllProjects() {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("workspace_id", this.workspaceId);

        return databaseClient.query(
                StatementLabel.FIND_PROJECTS_BY_WORKSPACE_ID,
                SELECT_PROJECTS_BY_WORKSPACE_ID,
                paramSource,
                ServerMappers.projectInfoMapper());
    }
}
