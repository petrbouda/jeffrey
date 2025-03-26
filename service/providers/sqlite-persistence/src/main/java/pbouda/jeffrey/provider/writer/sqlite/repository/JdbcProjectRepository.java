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
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import pbouda.jeffrey.common.model.GraphVisualization;
import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.provider.api.repository.ProjectRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class JdbcProjectRepository implements ProjectRepository {

    //language=SQL
    private static final String SELECT_ALL_PROFILES = """
            SELECT * FROM profiles WHERE project_id = :project_id
            """;

    //language=SQL
    private static final String SELECT_SINGLE_PROJECT = """
            SELECT * FROM projects WHERE project_id = :project_id
            """;

    //language=SQL
    private static final String SELECT_GRAPH_VISUALIZATION = """
            SELECT graph_visualization FROM projects WHERE project_id = :project_id
            """;

    //language=SQL
    private static final String UPDATE_PROJECTS_NAME = """
            UPDATE projects SET project_name = :project_name WHERE project_id = :project_id
            """;

    //language=SQL
    private static final String DELETE_PROJECT = """
            BEGIN TRANSACTION;
            DELETE FROM schedulers WHERE project_id = '%project_id%';
            DELETE FROM repositories WHERE project_id = '%project_id%';
            DELETE FROM projects WHERE project_id = '%project_id%';
            COMMIT;
            """;

    private final String projectId;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcProjectRepository(String projectId, JdbcTemplate jdbcTemplate) {
        this.projectId = projectId;
        this.jdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    @Override
    public void delete() {
        jdbcTemplate.getJdbcTemplate()
                .update(DELETE_PROJECT.replaceAll("%project_id%", projectId));
    }

    @Override
    public List<ProfileInfo> findAllProfiles() {
        Map<String, String> params = Map.of("project_id", projectId);
        return jdbcTemplate.query(SELECT_ALL_PROFILES, params, Mappers.profileInfoMapper());
    }

    @Override
    public Optional<ProjectInfo> find() {
        Map<String, String> params = Map.of("project_id", projectId);
        List<ProjectInfo> results = jdbcTemplate.query(SELECT_SINGLE_PROJECT, params, Mappers.projectInfoMapper());
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @Override
    public void updateProjectName(String name) {
        Map<String, Object> params = Map.of(
                "project_id", projectId,
                "project_name", name);

        jdbcTemplate.update(UPDATE_PROJECTS_NAME, params);
    }

    @Override
    public GraphVisualization findGraphVisualization() {
        Map<String, String> params = Map.of("project_id", projectId);
        return jdbcTemplate.queryForObject(SELECT_GRAPH_VISUALIZATION, params, Mappers.graphVisualizationMapper());
    }
}
