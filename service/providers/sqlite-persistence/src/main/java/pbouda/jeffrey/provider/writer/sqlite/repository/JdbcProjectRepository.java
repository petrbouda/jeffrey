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

package pbouda.jeffrey.provider.writer.sqlite.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.provider.api.repository.ProjectRepository;
import pbouda.jeffrey.provider.writer.sqlite.client.DatabaseClient;

import java.util.List;
import java.util.Optional;

public class JdbcProjectRepository implements ProjectRepository {

    //language=SQL
    private static final String SELECT_ALL_PROFILES =
            "SELECT * FROM profiles WHERE project_id = :project_id";

    //language=SQL
    private static final String SELECT_SINGLE_PROJECT =
            "SELECT * FROM projects WHERE project_id = :project_id";

    //language=SQL
    private static final String UPDATE_PROJECTS_NAME =
            "UPDATE projects SET project_name = :project_name WHERE project_id = :project_id";

    //language=SQL
    private static final String DELETE_PROJECT = """
            BEGIN TRANSACTION;
            DELETE FROM schedulers WHERE project_id = '%project_id%';
            DELETE FROM repositories WHERE project_id = '%project_id%';
            DELETE FROM recording_folders WHERE project_id = '%project_id%';
            DELETE FROM recordings WHERE project_id = '%project_id%';
            DELETE FROM external_project_links WHERE project_id = '%project_id%';
            DELETE FROM projects WHERE project_id = '%project_id%';
            COMMIT;""";

    private final String projectId;
    private final DatabaseClient databaseClient;

    public JdbcProjectRepository(String projectId, DatabaseClient databaseClient) {
        this.projectId = projectId;
        this.databaseClient = databaseClient;
    }

    @Override
    public void delete() {
        databaseClient.delete(DELETE_PROJECT.replaceAll("%project_id%", projectId));
    }

    @Override
    public List<ProfileInfo> findAllProfiles() {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId);

        return databaseClient.query(SELECT_ALL_PROFILES, paramSource, Mappers.profileInfoMapper());
    }

    @Override
    public Optional<ProjectInfo> find() {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId);

        return databaseClient.querySingle(SELECT_SINGLE_PROJECT, paramSource, Mappers.projectInfoMapper());
    }

    @Override
    public void updateProjectName(String name) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId)
                .addValue("project_name", name);

        databaseClient.update(UPDATE_PROJECTS_NAME, paramSource);
    }
}
