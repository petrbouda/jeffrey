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
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.model.ExternalProjectLink;
import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.provider.api.repository.ProjectsRepository;
import pbouda.jeffrey.provider.api.repository.model.CreateProject;
import pbouda.jeffrey.provider.writer.sqlite.client.DatabaseClient;

import javax.sql.DataSource;
import java.util.List;

public class JdbcProjectsRepository implements ProjectsRepository {

    //language=SQL
    private static final String SELECT_ALL_PROJECTS = "SELECT * FROM projects";

    //language=SQL
    private static final String INSERT_PROJECT = """
            INSERT INTO projects (
                 project_id,
                 project_name,
                 created_at,
                 graph_visualization)
                VALUES (:project_id, :project_name, :created_at, :graph_visualization)""";

    //language=SQL
    private static final String INSERT_EXTERNAL_PROJECT_LINK = """
            INSERT INTO external_project_links (
                project_id,
                external_component_id,
                external_component_type,
                original_source_type,
                original_source)
                VALUES (:project_id, :external_component_id, :external_component_type, :original_source_type, :original_source)""";

    //language=SQL
    private static final String FIND_EXTERNAL_PROJECT_LINK_BY_COMPONENT_ID =
            "SELECT * FROM external_project_links WHERE external_component_id = :external_component_id";

    private final DatabaseClient databaseClient;

    public JdbcProjectsRepository(DataSource dataSource) {
        this.databaseClient = new DatabaseClient(dataSource, "projects");
    }

    @Override
    public List<ProjectInfo> findAllProjects() {
        return databaseClient.query(SELECT_ALL_PROJECTS, Mappers.projectInfoMapper());
    }

    @Override
    public ExternalProjectLink createExternalProjectLink(ExternalProjectLink link) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", link.projectId())
                .addValue("external_component_id", link.externalComponentId())
                .addValue("external_component_type", link.externalComponentType().name())
                .addValue("original_source_type", link.originalSourceType().name())
                .addValue("original_source", link.original_source());

        databaseClient.insert(INSERT_EXTERNAL_PROJECT_LINK, paramSource);
        return link;
    }

    @Override
    public List<ExternalProjectLink> findExternalProjectLinks(String externalComponentId) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("external_component_id", externalComponentId);

        return databaseClient.query(
                FIND_EXTERNAL_PROJECT_LINK_BY_COMPONENT_ID, paramSource, Mappers.externalProjectLinkRowMapper());
    }

    @Override
    public ProjectInfo create(CreateProject project) {
        ProjectInfo newProject = project.projectInfo();

        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", newProject.id())
                .addValue("project_name", newProject.name())
                .addValue("created_at", newProject.createdAt().toEpochMilli())
                .addValue("graph_visualization", Json.toString(project.graphVisualization()));

        databaseClient.insert(INSERT_PROJECT, paramSource);
        return newProject;
    }
}
