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

package pbouda.jeffrey.provider.writer.sql.repository;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import pbouda.jeffrey.common.model.workspace.WorkspaceStatus;
import pbouda.jeffrey.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.common.model.workspace.WorkspaceLocation;
import pbouda.jeffrey.common.model.workspace.WorkspaceType;
import pbouda.jeffrey.provider.api.repository.WorkspacesRepository;
import pbouda.jeffrey.provider.writer.sql.GroupLabel;
import pbouda.jeffrey.provider.writer.sql.StatementLabel;
import pbouda.jeffrey.provider.writer.sql.client.DatabaseClient;
import pbouda.jeffrey.provider.writer.sql.client.DatabaseClientProvider;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class JdbcWorkspacesRepository implements WorkspacesRepository {

    //language=SQL
    private static final String SELECT_ALL_WORKSPACES = """
            SELECT w.*, (SELECT COUNT(*) FROM projects p WHERE p.workspace_id = w.workspace_id) as project_count
            FROM workspaces w WHERE w.deleted = false""";

    //language=SQL
    private static final String SELECT_WORKSPACE_BY_ID = """
            SELECT w.*, (SELECT COUNT(*) FROM projects p WHERE p.workspace_id = w.workspace_id) as project_count
            FROM workspaces w WHERE w.workspace_id = :workspace_id AND w.deleted = false""";

    //language=SQL
    private static final String INSERT_WORKSPACE = """
            INSERT INTO workspaces (workspace_id, repository_id, name, description, location, base_location, deleted, created_at, type)
            VALUES (:workspace_id, :repository_id, :name, :description, :location, :base_location, :deleted, :created_at, :type)""";

    //language=SQL
    private static final String CHECK_NAME_EXISTS =
            "SELECT COUNT(*) FROM workspaces WHERE name = :name AND deleted = false";

    private final DatabaseClient databaseClient;

    public JdbcWorkspacesRepository(DatabaseClientProvider databaseClientProvider) {
        this.databaseClient = databaseClientProvider.provide(GroupLabel.WORKSPACES);
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
    public WorkspaceInfo create(WorkspaceInfo workspaceInfo) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("workspace_id", workspaceInfo.id())
                .addValue("repository_id", workspaceInfo.repositoryId())
                .addValue("name", workspaceInfo.name())
                .addValue("description", workspaceInfo.description())
                .addValue("location", workspaceInfo.location() != null ? workspaceInfo.location().toString() : null)
                .addValue("base_location", workspaceInfo.baseLocation() != null ? workspaceInfo.baseLocation().toString() : null)
                .addValue("deleted", false)
                .addValue("created_at", workspaceInfo.createdAt().toEpochMilli())
                .addValue("type", workspaceInfo.type().name());

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
        return (rs, _) -> {
            String location = rs.getString("location");
            String baseLocation = rs.getString("base_location");

            return new WorkspaceInfo(
                    rs.getString("workspace_id"),
                    rs.getString("repository_id"),
                    rs.getString("name"),
                    rs.getString("description"),
                    location != null ? WorkspaceLocation.of(location) : null,
                    baseLocation != null ? WorkspaceLocation.of(baseLocation) : null,
                    Instant.ofEpochMilli(rs.getLong("created_at")),
                    WorkspaceType.valueOf(rs.getString("type")),
                    WorkspaceStatus.UNKNOWN,
                    rs.getInt("project_count")
            );
        };
    }
}
