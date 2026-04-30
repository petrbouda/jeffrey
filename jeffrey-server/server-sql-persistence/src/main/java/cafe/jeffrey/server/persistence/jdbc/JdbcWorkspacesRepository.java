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

package cafe.jeffrey.server.persistence.jdbc;

import cafe.jeffrey.server.persistence.api.WorkspacesRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import cafe.jeffrey.shared.common.IDGenerator;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceLocation;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceStatus;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

public class JdbcWorkspacesRepository implements WorkspacesRepository {

    //language=SQL
    private static final String SELECT_ALL_WORKSPACES = """
            SELECT w.*, (SELECT COUNT(*) FROM projects p WHERE p.workspace_id = w.workspace_id AND p.deleted_at IS NULL) as project_count
            FROM workspaces w""";

    //language=SQL
    private static final String SELECT_WORKSPACE_BY_ID = """
            SELECT w.*, (SELECT COUNT(*) FROM projects p WHERE p.workspace_id = w.workspace_id AND p.deleted_at IS NULL) as project_count
            FROM workspaces w WHERE w.workspace_id = :workspace_id""";

    //language=SQL
    private static final String SELECT_WORKSPACE_BY_REFERENCE_ID = """
            SELECT w.*, (SELECT COUNT(*) FROM projects p WHERE p.workspace_id = w.workspace_id AND p.deleted_at IS NULL) as project_count
            FROM workspaces w WHERE w.reference_id = :reference_id""";

    //language=SQL
    private static final String INSERT_WORKSPACE = """
            INSERT INTO workspaces (workspace_id, reference_id, repository_id, name, location, base_location, created_at)
            VALUES (:workspace_id, :reference_id, :repository_id, :name, :location, :base_location, :created_at)""";

    //language=SQL
    private static final String CHECK_NAME_EXISTS =
            "SELECT COUNT(*) FROM workspaces WHERE name = :name";

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
    public Optional<WorkspaceInfo> findByReferenceId(String referenceId) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("reference_id", referenceId);

        return databaseClient.querySingle(
                StatementLabel.FIND_WORKSPACE_BY_REFERENCE_ID,
                SELECT_WORKSPACE_BY_REFERENCE_ID,
                paramSource,
                workspaceMapper());
    }

    @Override
    public WorkspaceInfo create(WorkspaceInfo workspaceInfo) {
        WorkspaceInfo newWorkspaceInfo = workspaceInfo.withId(IDGenerator.generate());
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("workspace_id", newWorkspaceInfo.id())
                .addValue("reference_id", newWorkspaceInfo.referenceId())
                .addValue("repository_id", newWorkspaceInfo.repositoryId())
                .addValue("name", newWorkspaceInfo.name())
                .addValue("location", newWorkspaceInfo.location() != null ? newWorkspaceInfo.location().toString() : null)
                .addValue("base_location", newWorkspaceInfo.baseLocation() != null ? newWorkspaceInfo.baseLocation().toString() : null)
                .addValue("created_at", newWorkspaceInfo.createdAt().atOffset(ZoneOffset.UTC));

        databaseClient.update(StatementLabel.INSERT_WORKSPACE, INSERT_WORKSPACE, paramSource);
        return newWorkspaceInfo;
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
                    rs.getString("reference_id"),
                    rs.getString("repository_id"),
                    rs.getString("name"),
                    location != null ? WorkspaceLocation.of(location) : null,
                    baseLocation != null ? WorkspaceLocation.of(baseLocation) : null,
                    ServerMappers.instant(rs, "created_at"),
                    WorkspaceStatus.UNKNOWN,
                    rs.getInt("project_count")
            );
        };
    }
}
