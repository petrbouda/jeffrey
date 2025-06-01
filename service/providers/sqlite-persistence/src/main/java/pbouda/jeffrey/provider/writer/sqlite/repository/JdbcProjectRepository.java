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

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import pbouda.jeffrey.common.model.GraphVisualization;
import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.provider.api.repository.ProjectRepository;

import javax.sql.DataSource;
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
    private static final String SELECT_GRAPH_VISUALIZATION =
            "SELECT graph_visualization FROM projects WHERE project_id = :project_id";

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
    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;

    public JdbcProjectRepository(String projectId, JdbcClient jdbcClient, DataSource dataSource) {
        this.projectId = projectId;
        this.jdbcClient = jdbcClient;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public void delete() {
        jdbcTemplate.update(DELETE_PROJECT.replaceAll("%project_id%", projectId));
    }

    @Override
    public List<ProfileInfo> findAllProfiles() {
        return jdbcClient.sql(SELECT_ALL_PROFILES)
                .param("project_id", projectId)
                .query(Mappers.profileInfoMapper())
                .list();
    }

    @Override
    public Optional<ProjectInfo> find() {
        return jdbcClient.sql(SELECT_SINGLE_PROJECT)
                .param("project_id", projectId)
                .query(Mappers.projectInfoMapper())
                .optional();
    }

    @Override
    public void updateProjectName(String name) {
        jdbcClient.sql(UPDATE_PROJECTS_NAME)
                .param("project_id", projectId)
                .param("project_name", name)
                .update();
    }

    @Override
    public GraphVisualization findGraphVisualization() {
        return jdbcClient.sql(SELECT_GRAPH_VISUALIZATION)
                .param("project_id", projectId)
                .query(Mappers.graphVisualizationMapper())
                .single();
    }
}
