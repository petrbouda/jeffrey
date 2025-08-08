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
import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.provider.api.repository.ProjectsRepository;
import pbouda.jeffrey.provider.api.repository.model.CreateProject;
import pbouda.jeffrey.provider.writer.sqlite.GroupLabel;
import pbouda.jeffrey.provider.writer.sqlite.StatementLabel;
import pbouda.jeffrey.provider.writer.sqlite.client.DatabaseClient;

import javax.sql.DataSource;
import java.util.List;

public class JdbcProjectsRepository implements ProjectsRepository {

    //language=SQL
    private static final String SELECT_ALL_PROJECTS = "SELECT * FROM projects";

    //language=SQL
    private static final String SELECT_PROJECTS_BY_WORKSPACE = "SELECT * FROM projects WHERE workspace_id = :workspace_id";

    //language=SQL
    private static final String SELECT_PROJECTS_BY_NULL_WORKSPACE = "SELECT * FROM projects WHERE workspace_id IS NULL";

    //language=SQL
    private static final String INSERT_PROJECT = """
            INSERT INTO projects (
                 project_id,
                 project_name,
                 workspace_id,
                 created_at,
                 attributes,
                 graph_visualization)
                VALUES (:project_id, :project_name, :workspace_id, :created_at, :attributes: graph_visualization)""";

    private final DatabaseClient databaseClient;

    public JdbcProjectsRepository(DataSource dataSource) {
        this.databaseClient = new DatabaseClient(dataSource, GroupLabel.PROJECTS);
    }

    @Override
    public List<ProjectInfo> findAllProjects() {
        return databaseClient.query(StatementLabel.FIND_ALL_PROJECTS, SELECT_ALL_PROJECTS, Mappers.projectInfoMapper());
    }

    @Override
    public List<ProjectInfo> findAllProjects(String workspaceId) {
        if (workspaceId == null) {
            return databaseClient.query(StatementLabel.FIND_PROJECTS_BY_WORKSPACE,
                    SELECT_PROJECTS_BY_NULL_WORKSPACE, Mappers.projectInfoMapper());
        } else {
            MapSqlParameterSource paramSource = new MapSqlParameterSource()
                    .addValue("workspace_id", workspaceId);
            return databaseClient.query(StatementLabel.FIND_PROJECTS_BY_WORKSPACE,
                    SELECT_PROJECTS_BY_WORKSPACE, paramSource, Mappers.projectInfoMapper());
        }
    }

    @Override
    public ProjectInfo create(CreateProject project) {
        ProjectInfo newProject = project.projectInfo();

        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", newProject.id())
                .addValue("project_name", newProject.name())
                .addValue("workspace_id", newProject.workspaceId())
                .addValue("created_at", newProject.createdAt().toEpochMilli())
                .addValue("attributes", Json.toString(project.graphVisualization()))
                .addValue("graph_visualization", Json.toString(project.graphVisualization()));

        databaseClient.insert(StatementLabel.INSERT_PROJECT, INSERT_PROJECT, paramSource);
        return newProject;
    }
}
