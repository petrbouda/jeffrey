/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.hub.persistence.jdbc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.persistence.api.ProjectsRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

public class JdbcProjectsRepository implements ProjectsRepository {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcProjectsRepository.class);

    //language=SQL
    private static final String SELECT_PROJECT_BY_ORIGIN_ID = """
            SELECT p.* FROM projects p
            WHERE EXISTS (SELECT 1 FROM workspaces w WHERE w.workspace_id = p.workspace_id)
              AND p.origin_project_id = :origin_project_id""";

    //language=SQL
    private static final String SELECT_ALL_PROJECTS = """
            SELECT p.* FROM projects p
            WHERE EXISTS (SELECT 1 FROM workspaces w WHERE w.workspace_id = p.workspace_id)""";

    //language=SQL
    private static final String SELECT_PROJECTS_BY_WORKSPACE = """
            SELECT p.* FROM projects p
            WHERE EXISTS (SELECT 1 FROM workspaces w WHERE w.workspace_id = p.workspace_id)
              AND p.workspace_id = :workspace_id""";

    //language=SQL
    private static final String INSERT_PROJECT = """
            INSERT INTO projects (
                 project_id,
                 origin_project_id,
                 project_name,
                 workspace_id,
                 created_at,
                 origin_created_at,
                 attributes)
                SELECT :project_id, :origin_project_id, :project_name, :workspace_id, :created_at, :origin_created_at, :attributes
                WHERE NOT EXISTS (SELECT 1 FROM projects WHERE origin_project_id = :origin_project_id AND origin_project_id IS NOT NULL)
                ON CONFLICT DO NOTHING""";

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
    public ProjectInfo create(ProjectInfo newProject) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", newProject.id())
                .addValue("origin_project_id", newProject.originId())
                .addValue("project_name", newProject.name())
                .addValue("workspace_id", newProject.workspaceId())
                .addValue("created_at", newProject.createdAt().atOffset(ZoneOffset.UTC))
                .addValue("origin_created_at", newProject.originCreatedAt() != null ? newProject.originCreatedAt().atOffset(ZoneOffset.UTC) : null)
                .addValue("attributes", Json.toString(newProject.attributes()));

        int inserted = databaseClient.insert(StatementLabel.INSERT_PROJECT, INSERT_PROJECT, paramSource);
        if (inserted == 1) {
            return newProject;
        }

        // The insert was skipped on the origin id's unique index: the project already exists
        // under another row, and that row is the answer — said out loud, because the caller
        // asked to create one and is being handed one it did not create
        if (newProject.originId() != null) {
            Optional<ProjectInfo> existing = findByOriginProjectId(newProject.originId());
            if (existing.isPresent()) {
                LOG.warn("Project already exists for origin, returning the existing one: origin_project_id={} project_id={}",
                        newProject.originId(), existing.get().id());
                return existing.get();
            }
        }
        // Should not happen for locally generated IDs — make it visible instead of
        // silently returning a project object that was never persisted
        LOG.warn("Project insert was skipped (conflicting project_id): project_id={}", newProject.id());
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
}
