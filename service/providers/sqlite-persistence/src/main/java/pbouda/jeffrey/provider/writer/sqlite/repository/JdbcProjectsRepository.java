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

import org.springframework.jdbc.core.simple.JdbcClient;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.model.ExternalProjectLink;
import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.provider.api.repository.ProjectsRepository;
import pbouda.jeffrey.provider.api.repository.model.CreateProject;

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
                VALUES (?, ?, ?, ?)""";

    //language=SQL
    private static final String INSERT_EXTERNAL_PROJECT_LINK = """
            INSERT INTO external_project_links (
                project_id,
                external_component_id,
                external_component_type,
                original_source_type,
                original_source)
                VALUES (?, ?, ?, ?, ?)""";

    //language=SQL
    private static final String FIND_EXTERNAL_PROJECT_LINK_BY_COMPONENT_ID =
            "SELECT * FROM external_project_links WHERE external_component_id = ?";

    private final JdbcClient jdbcClient;

    public JdbcProjectsRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public List<ProjectInfo> findAllProjects() {
        return jdbcClient.sql(SELECT_ALL_PROJECTS)
                .query(Mappers.projectInfoMapper())
                .list();
    }

    @Override
    public ExternalProjectLink createExternalProjectLink(ExternalProjectLink link) {
        jdbcClient.sql(INSERT_EXTERNAL_PROJECT_LINK)
                .param(link.projectId())
                .param(link.externalComponentId())
                .param(link.externalComponentType().name())
                .param(link.originalSourceType().name())
                .param(link.original_source())
                .update();

        return link;
    }

    @Override
    public List<ExternalProjectLink> findExternalProjectLinks(String externalComponentId) {
        return jdbcClient.sql(FIND_EXTERNAL_PROJECT_LINK_BY_COMPONENT_ID)
                .param(externalComponentId)
                .query(Mappers.externalProjectLinkRowMapper())
                .list();
    }

    @Override
    public ProjectInfo create(CreateProject project) {
        ProjectInfo newProject = project.projectInfo();
        jdbcClient.sql(INSERT_PROJECT)
                .param(newProject.id())
                .param(newProject.name())
                .param(newProject.createdAt().toEpochMilli())
                .param(Json.toString(project.graphVisualization()))
                .update();
        return newProject;
    }
}
