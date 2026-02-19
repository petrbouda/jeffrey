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
import pbouda.jeffrey.shared.common.model.ProfileInfo;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.persistence.GroupLabel;
import pbouda.jeffrey.shared.persistence.StatementLabel;
import pbouda.jeffrey.shared.persistence.client.DatabaseClient;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.util.List;
import java.util.Optional;

public class JdbcProjectRepository implements ProjectRepository {

    //language=SQL
    private static final String SELECT_ALL_PROFILES = """
            SELECT p.*, proj.workspace_id
            FROM profiles p
            JOIN projects proj ON p.project_id = proj.project_id
            WHERE p.project_id = :project_id""";

    //language=SQL
    private static final String SELECT_SINGLE_PROJECT = """
            SELECT * FROM projects p
            JOIN workspaces w ON p.workspace_id = w.workspace_id
            WHERE p.project_id = :project_id""";

    //language=SQL
    private static final String UPDATE_PROJECTS_NAME =
            "UPDATE projects SET project_name = :project_name WHERE project_id = :project_id";

    //language=SQL
    private static final String DELETE_SCHEDULERS =
            "DELETE FROM schedulers WHERE project_id = :project_id";
    //language=SQL
    private static final String DELETE_SESSIONS =
            "DELETE FROM project_instance_sessions WHERE repository_id IN (SELECT repository_id FROM repositories WHERE project_id = :project_id)";
    //language=SQL
    private static final String DELETE_INSTANCES =
            "DELETE FROM project_instances WHERE project_id = :project_id";
    //language=SQL
    private static final String DELETE_REPOSITORIES =
            "DELETE FROM repositories WHERE project_id = :project_id";
    //language=SQL
    private static final String DELETE_FOLDERS =
            "DELETE FROM recording_folders WHERE project_id = :project_id";
    //language=SQL
    private static final String DELETE_FILES =
            "DELETE FROM recording_files WHERE project_id = :project_id";
    //language=SQL
    private static final String DELETE_RECORDINGS =
            "DELETE FROM recordings WHERE project_id = :project_id";
    //language=SQL
    private static final String DELETE_PROFILER_SETTINGS =
            "DELETE FROM profiler_settings WHERE project_id = :project_id";
    //language=SQL
    private static final String DELETE_MESSAGES =
            "DELETE FROM messages WHERE project_id = :project_id";
    //language=SQL
    private static final String DELETE_ALERTS =
            "DELETE FROM alerts WHERE project_id = :project_id";
    //language=SQL
    private static final String DELETE_PROFILES =
            "DELETE FROM profiles WHERE project_id = :project_id";
    //language=SQL
    private static final String DELETE_PROJECT_ENTRY =
            "DELETE FROM projects WHERE project_id = :project_id";

    private final String projectId;
    private final DatabaseClient databaseClient;

    public JdbcProjectRepository(String projectId, DatabaseClientProvider databaseClientProvider) {
        this.projectId = projectId;
        this.databaseClient = databaseClientProvider.provide(GroupLabel.SINGLE_PROJECT);
    }

    @Override
    public void delete() {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("project_id", projectId);

        // Order matters: children before parents
        databaseClient.delete(StatementLabel.DELETE_PROJECT_SCHEDULERS, DELETE_SCHEDULERS, params);
        databaseClient.delete(StatementLabel.DELETE_PROJECT_SESSIONS, DELETE_SESSIONS, params);
        databaseClient.delete(StatementLabel.DELETE_PROJECT_INSTANCES, DELETE_INSTANCES, params);
        databaseClient.delete(StatementLabel.DELETE_PROJECT_REPOSITORIES, DELETE_REPOSITORIES, params);
        databaseClient.delete(StatementLabel.DELETE_PROJECT_FOLDERS, DELETE_FOLDERS, params);
        databaseClient.delete(StatementLabel.DELETE_PROJECT_FILES, DELETE_FILES, params);
        databaseClient.delete(StatementLabel.DELETE_PROJECT_RECORDINGS, DELETE_RECORDINGS, params);
        databaseClient.delete(StatementLabel.DELETE_PROJECT_PROFILER_SETTINGS, DELETE_PROFILER_SETTINGS, params);
        databaseClient.delete(StatementLabel.DELETE_PROJECT_MESSAGES, DELETE_MESSAGES, params);
        databaseClient.delete(StatementLabel.DELETE_PROJECT_ALERTS, DELETE_ALERTS, params);
        databaseClient.delete(StatementLabel.DELETE_PROJECT_PROFILES, DELETE_PROFILES, params);
        databaseClient.delete(StatementLabel.DELETE_PROJECT, DELETE_PROJECT_ENTRY, params);
    }

    @Override
    public List<ProfileInfo> findAllProfiles() {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId);

        return databaseClient.query(
                StatementLabel.FIND_ALL_PROFILES, SELECT_ALL_PROFILES, paramSource, Mappers.profileInfoMapper());
    }

    @Override
    public Optional<ProjectInfo> find() {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId);

        return databaseClient.querySingle(
                StatementLabel.FIND_PROJECT, SELECT_SINGLE_PROJECT, paramSource, Mappers.projectInfoMapper());
    }

    @Override
    public void updateProjectName(String name) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId)
                .addValue("project_name", name);

        databaseClient.update(StatementLabel.UPDATE_PROJECT_NAME, UPDATE_PROJECTS_NAME, paramSource);
    }
}
