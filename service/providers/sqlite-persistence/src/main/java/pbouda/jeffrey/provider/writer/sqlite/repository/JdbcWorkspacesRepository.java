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

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import pbouda.jeffrey.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.common.model.workspace.WorkspaceLocation;
import pbouda.jeffrey.provider.api.repository.WorkspacesRepository;
import pbouda.jeffrey.provider.writer.sqlite.GroupLabel;
import pbouda.jeffrey.provider.writer.sqlite.StatementLabel;
import pbouda.jeffrey.provider.writer.sqlite.client.DatabaseClient;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class JdbcWorkspacesRepository implements WorkspacesRepository {

    //language=SQL
    private static final String SELECT_ALL_WORKSPACES = """
            SELECT w.*, (SELECT COUNT(*) FROM main.projects p WHERE p.workspace_id = w.workspace_id) as project_count
            FROM main.workspaces w WHERE w.enabled = true""";

    //language=SQL
    private static final String SELECT_WORKSPACE_BY_ID = """
            SELECT w.*, (SELECT COUNT(*) FROM main.projects p WHERE p.workspace_id = w.workspace_id) as project_count
            FROM main.workspaces w WHERE w.workspace_id = :workspace_id AND w.enabled = true""";

    //language=SQL
    private static final String SELECT_WORKSPACE_BY_REPOSITORY_ID = """
            SELECT w.*, (SELECT COUNT(*) FROM main.projects p WHERE p.workspace_id = w.workspace_id) as project_count
            FROM main.workspaces w WHERE w.repository_id = :repository_id AND w.enabled = true""";

    //language=SQL
    private static final String INSERT_WORKSPACE = """
            INSERT INTO main.workspaces (workspace_id, repository_id, name, description, location, enabled, created_at, mirrored)
            VALUES (:workspace_id, :repository_id, :name, :description, :location, :enabled, :created_at, :mirrored)""";

    //language=SQL
    private static final String CHECK_NAME_EXISTS =
            "SELECT COUNT(*) FROM main.workspaces WHERE name = :name AND enabled = true";

    private final DatabaseClient databaseClient;

    public JdbcWorkspacesRepository(DataSource dataSource) {
        this.databaseClient = new DatabaseClient(dataSource, GroupLabel.WORKSPACES);
    }

    @Override
    public List<WorkspaceInfo> findAll() {
        return databaseClient.query(
                StatementLabel.FIND_ALL_WORKSPACES,
                SELECT_ALL_WORKSPACES,
                new MapSqlParameterSource(),
                workspaceMapper());
    }

    @Override
    public Optional<WorkspaceInfo> find(String workspaceId) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("workspace_id", workspaceId);

        return databaseClient.querySingle(
                StatementLabel.FIND_WORKSPACE_BY_ID,
                SELECT_WORKSPACE_BY_ID,
                paramSource,
                workspaceMapper());
    }

    @Override
    public Optional<WorkspaceInfo> findByRepositoryId(String repositoryId) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("repository_id", repositoryId);

        return databaseClient.querySingle(
                StatementLabel.FIND_WORKSPACE_BY_REPOSITORY_ID,
                SELECT_WORKSPACE_BY_REPOSITORY_ID,
                paramSource,
                workspaceMapper());
    }

    @Override
    public WorkspaceInfo create(WorkspaceInfo workspaceInfo) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("workspace_id", workspaceInfo.id())
                .addValue("repository_id", workspaceInfo.repositoryId())
                .addValue("name", workspaceInfo.name())
                .addValue("description", workspaceInfo.description())
                .addValue("location", workspaceInfo.location().toString())
                .addValue("enabled", workspaceInfo.enabled())
                .addValue("created_at", workspaceInfo.createdAt().toEpochMilli())
                .addValue("mirrored", workspaceInfo.isMirrored());

        databaseClient.update(StatementLabel.INSERT_WORKSPACE, INSERT_WORKSPACE, paramSource);
        return workspaceInfo;
    }

    @Override
    public boolean existsByName(String name) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("name", name);

        Integer count = databaseClient.querySingle(
                StatementLabel.CHECK_NAME_EXISTS,
                CHECK_NAME_EXISTS,
                paramSource,
                (rs, _) -> rs.getInt(1)
        ).orElse(0);

        return count > 0;
    }

    private static RowMapper<WorkspaceInfo> workspaceMapper() {
        return (rs, _) -> new WorkspaceInfo(
                rs.getString("workspace_id"),
                rs.getString("repository_id"),
                rs.getString("name"),
                rs.getString("description"),
                WorkspaceLocation.of(rs.getString("location")),
                rs.getBoolean("enabled"),
                Instant.ofEpochMilli(rs.getLong("created_at")),
                rs.getBoolean("mirrored"),
                rs.getInt("project_count")
        );
    }
}
