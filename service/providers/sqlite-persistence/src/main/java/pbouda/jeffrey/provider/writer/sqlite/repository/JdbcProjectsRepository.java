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
import pbouda.jeffrey.common.IDGenerator;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.provider.api.repository.ProjectsRepository;
import pbouda.jeffrey.provider.api.repository.model.CreateProject;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class JdbcProjectsRepository implements ProjectsRepository {

    //language=SQL
    private static final String SELECT_ALL_PROJECTS = """
            SELECT * FROM projects
            """;

    //language=SQL
    private static final String INSERT_PROJECT = """
            INSERT INTO projects (
                 project_id,
                 project_name,
                 created_at,
                 graph_visualization)
                VALUES (:project_id,
                        :project_name,
                        :created_at,
                        :graph_visualization)
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcProjectsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    @Override
    public List<ProjectInfo> findAllProjects() {
        return jdbcTemplate.query(SELECT_ALL_PROJECTS, Mappers.projectInfoMapper());
    }

    @Override
    public ProjectInfo create(CreateProject project) {
        ProjectInfo newProject = project.projectInfo();
        Map<String, Object> params = Map.of(
                "project_id", newProject.id(),
                "project_name", newProject.name(),
                "created_at", newProject.createdAt().toEpochMilli(),
                "graph_visualization", Json.toString(project.graphVisualization()));

        jdbcTemplate.update(INSERT_PROJECT, params);
        return newProject;
    }
}
