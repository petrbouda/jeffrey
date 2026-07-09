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

package cafe.jeffrey.hub.persistence.jdbc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.persistence.api.ProjectsRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.hub.persistence.api.CreateProject;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

public class JdbcProjectsRepository implements ProjectsRepository {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcProjectsRepository.class);

    //language=SQL
    private static final String SELECT_PROJECT_BY_ORIGIN_ID = """
            SELECT * FROM projects p
            JOIN workspaces w ON p.workspace_id = w.workspace_id
            WHERE p.origin_project_id = :origin_project_id AND p.deleted_at IS NULL""";

    //language=SQL
    private static final String SELECT_ALL_PROJECTS = """
            SELECT * FROM projects p
            JOIN workspaces w ON p.workspace_id = w.workspace_id
            WHERE p.deleted_at IS NULL""";

    //language=SQL
    private static final String SELECT_PROJECTS_BY_WORKSPACE = """
            SELECT * FROM projects p
            JOIN workspaces w ON p.workspace_id = w.workspace_id
            WHERE p.workspace_id = :workspace_id AND p.deleted_at IS NULL""";

    //language=SQL
    private static final String SELECT_PROJECTS_BY_WORKSPACE_INCLUDING_DELETED = """
            SELECT * FROM projects p
            JOIN workspaces w ON p.workspace_id = w.workspace_id
            WHERE p.workspace_id = :workspace_id""";

    //language=SQL
    private static final String INSERT_PROJECT = """
            INSERT INTO projects (
                 project_id,
                 origin_project_id,
                 project_name,
                 project_label,
                 namespace,
                 workspace_id,
                 created_at,
                 origin_created_at,
                 attributes,
                 graph_visualization)
                SELECT :project_id, :origin_project_id, :project_name, :project_label, :namespace, :workspace_id, :created_at, :origin_created_at, :attributes, :graph_visualization
                WHERE NOT EXISTS (SELECT 1 FROM projects WHERE origin_project_id = :origin_project_id AND origin_project_id IS NOT NULL AND deleted_at IS NULL)
                ON CONFLICT DO NOTHING""";

    //language=SQL
    private static final String SELECT_ALL_NAMESPACES = """
            SELECT DISTINCT namespace FROM projects
            WHERE namespace IS NOT NULL AND deleted_at IS NULL
            ORDER BY namespace""";

    /**
     * Children are normally removed when the project is soft-deleted; the child deletes here
     * are defensive so a purge never strands rows that survived an interrupted soft-delete.
     * The purge of the {@code projects} rows themselves must run last.
     */
    //language=SQL
    private static final List<String> PURGE_DELETED_PROJECTS_CASCADE = List.of(
            """
            DELETE FROM project_instance_sessions WHERE repository_id IN (
                SELECT r.repository_id FROM repositories r
                JOIN projects p ON r.project_id = p.project_id
                WHERE p.deleted_at < :deleted_before)""",
            "DELETE FROM project_instances WHERE project_id IN (SELECT project_id FROM projects WHERE deleted_at < :deleted_before)",
            "DELETE FROM repositories WHERE project_id IN (SELECT project_id FROM projects WHERE deleted_at < :deleted_before)",
            "DELETE FROM profiler_settings WHERE project_id IN (SELECT project_id FROM projects WHERE deleted_at < :deleted_before)",
            "DELETE FROM projects WHERE deleted_at < :deleted_before");

    private final DatabaseClient databaseClient;

    public JdbcProjectsRepository(DatabaseClientProvider databaseClientProvider) {
        this.databaseClient = databaseClientProvider.provide(GroupLabel.PROJECTS);
    }

    @Override
    public List<ProjectInfo> findAllProjects() {
        return databaseClient.query(StatementLabel.FIND_ALL_PROJECTS, SELECT_ALL_PROJECTS, HubMappers.projectInfoMapper());
    }

    @Override
    public List<ProjectInfo> findAllProjects(String workspaceId) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("workspace_id", workspaceId);
        return databaseClient.query(StatementLabel.FIND_PROJECTS_BY_WORKSPACE,
                SELECT_PROJECTS_BY_WORKSPACE, paramSource, HubMappers.projectInfoMapper());
    }

    @Override
    public List<ProjectInfo> findAllProjectsIncludingDeleted(String workspaceId) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("workspace_id", workspaceId);
        return databaseClient.query(StatementLabel.FIND_PROJECTS_BY_WORKSPACE_INCLUDING_DELETED,
                SELECT_PROJECTS_BY_WORKSPACE_INCLUDING_DELETED, paramSource, HubMappers.projectInfoMapper());
    }

    @Override
    public ProjectInfo create(CreateProject project) {
        ProjectInfo newProject = project.projectInfo();

        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", newProject.id())
                .addValue("origin_project_id", newProject.originId())
                .addValue("project_name", newProject.name())
                .addValue("project_label", newProject.label())
                .addValue("namespace", newProject.namespace())
                .addValue("workspace_id", newProject.workspaceId())
                .addValue("created_at", newProject.createdAt().atOffset(ZoneOffset.UTC))
                .addValue("origin_created_at", newProject.originCreatedAt() != null ? newProject.originCreatedAt().atOffset(ZoneOffset.UTC) : null)
                .addValue("attributes", Json.toString(newProject.attributes()))
                .addValue("graph_visualization", Json.toString(project.graphVisualization()));

        int inserted = databaseClient.insert(StatementLabel.INSERT_PROJECT, INSERT_PROJECT, paramSource);

        if (newProject.originId() != null) {
            return findByOriginProjectId(newProject.originId())
                    .orElse(newProject);
        }
        if (inserted == 0) {
            // Should not happen for locally generated IDs — make it visible instead of
            // silently returning a project object that was never persisted
            LOG.warn("Project insert was skipped (conflicting project_id): project_id={}", newProject.id());
        }
        return newProject;
    }

    @Override
    public Optional<ProjectInfo> findByOriginProjectId(String originProjectId) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("origin_project_id", originProjectId);

        return databaseClient.querySingle(
                StatementLabel.FIND_PROJECT_BY_ORIGIN_ID, SELECT_PROJECT_BY_ORIGIN_ID, paramSource,
                HubMappers.projectInfoMapper());
    }

    @Override
    public List<String> findAllNamespaces() {
        return databaseClient.query(
                StatementLabel.FIND_ALL_PROJECT_NAMESPACES,
                SELECT_ALL_NAMESPACES,
                (rs, _) -> rs.getString("namespace"));
    }

    @Override
    public int purgeDeletedProjects(Instant deletedBefore) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("deleted_before", deletedBefore.atOffset(ZoneOffset.UTC));

        int purgedProjects = 0;
        for (String sql : PURGE_DELETED_PROJECTS_CASCADE) {
            purgedProjects = databaseClient.delete(StatementLabel.PURGE_DELETED_PROJECTS, sql, paramSource);
        }
        // The last statement of the cascade deletes the projects rows themselves
        return purgedProjects;
    }
}
