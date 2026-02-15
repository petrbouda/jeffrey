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

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.persistence.GroupLabel;
import pbouda.jeffrey.shared.persistence.StatementLabel;
import pbouda.jeffrey.shared.persistence.client.DatabaseClient;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.time.Clock;
import java.util.List;

public class JdbcWorkspaceRepository implements WorkspaceRepository {

    //language=SQL
    private static final String DELETE_WORKSPACE =
            "UPDATE workspaces SET deleted = true WHERE workspace_id = :workspace_id";

    //language=SQL
    private static final String SELECT_PROJECTS_BY_WORKSPACE_ID = """
            SELECT * FROM projects
            JOIN workspaces ON projects.workspace_id = workspaces.workspace_id
            WHERE workspaces.workspace_id = :workspace_id""";

    private final String workspaceId;
    private final DatabaseClient databaseClient;

    public JdbcWorkspaceRepository(String workspaceId, DatabaseClientProvider databaseClientProvider, Clock clock) {
        this.workspaceId = workspaceId;
        this.databaseClient = databaseClientProvider.provide(GroupLabel.WORKSPACES);
    }

    @Override
    public boolean delete() {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("workspace_id", this.workspaceId);

        int rowsAffected = databaseClient.update(StatementLabel.DELETE_WORKSPACE, DELETE_WORKSPACE, paramSource);
        return rowsAffected > 0;
    }

    @Override
    public List<ProjectInfo> findAllProjects() {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("workspace_id", this.workspaceId);

        return databaseClient.query(
                StatementLabel.FIND_PROJECTS_BY_WORKSPACE_ID,
                SELECT_PROJECTS_BY_WORKSPACE_ID,
                paramSource,
                Mappers.projectInfoMapper());
    }
}
