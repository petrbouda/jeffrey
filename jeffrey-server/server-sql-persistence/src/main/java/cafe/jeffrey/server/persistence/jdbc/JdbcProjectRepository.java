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

package cafe.jeffrey.server.persistence.jdbc;

import cafe.jeffrey.server.persistence.api.ProjectRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.util.Optional;

public class JdbcProjectRepository implements ProjectRepository {

    //language=SQL
    private static final String SELECT_SINGLE_PROJECT = """
            SELECT * FROM projects p
            JOIN workspaces w ON p.workspace_id = w.workspace_id
            WHERE p.project_id = :project_id AND p.deleted_at IS NULL""";

    //language=SQL
    private static final String UPDATE_PROJECTS_NAME =
            "UPDATE projects SET project_name = :project_name WHERE project_id = :project_id";

    //language=SQL
    private static final String RESTORE_PROJECT =
            "UPDATE projects SET deleted_at = NULL WHERE project_id = :project_id";

    //language=SQL
    private static final String DELETE_PROJECT_CASCADE = """
            DELETE FROM schedulers WHERE project_id = '%project_id%';
            DELETE FROM project_instance_sessions WHERE repository_id IN (SELECT repository_id FROM repositories WHERE project_id = '%project_id%');
            DELETE FROM project_instances WHERE project_id = '%project_id%';
            DELETE FROM repositories WHERE project_id = '%project_id%';
            DELETE FROM profiler_settings WHERE project_id = '%project_id%';
            UPDATE projects SET deleted_at = CURRENT_TIMESTAMP WHERE project_id = '%project_id%'""";

    private final String projectId;
    private final DatabaseClient databaseClient;

    public JdbcProjectRepository(String projectId, DatabaseClientProvider databaseClientProvider) {
        this.projectId = projectId;
        this.databaseClient = databaseClientProvider.provide(GroupLabel.SINGLE_PROJECT);
    }

    @Override
    public void delete() {
        String sql = DELETE_PROJECT_CASCADE.replaceAll("%project_id%", projectId);
        databaseClient.delete(StatementLabel.DELETE_PROJECT, sql);
    }

    @Override
    public Optional<ProjectInfo> find() {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId);

        return databaseClient.querySingle(
                StatementLabel.FIND_PROJECT, SELECT_SINGLE_PROJECT, paramSource, ServerMappers.projectInfoMapper());
    }

    @Override
    public void updateProjectName(String name) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId)
                .addValue("project_name", name);

        databaseClient.update(StatementLabel.UPDATE_PROJECT_NAME, UPDATE_PROJECTS_NAME, paramSource);
    }

    @Override
    public void restore() {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId);

        databaseClient.update(StatementLabel.RESTORE_PROJECT, RESTORE_PROJECT, paramSource);
    }

}
