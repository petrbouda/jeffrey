/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.local.persistence.jdbc;

import cafe.jeffrey.local.persistence.api.*;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import cafe.jeffrey.local.persistence.api.RemoteWorkspaceInfo;
import cafe.jeffrey.local.persistence.api.WorkspaceAddress;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceStatus;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Minimal local registry of remote workspace connections.
 * Only stores connection reference (workspace_id, base_location).
 * All other workspace data (name, description, status, project count) comes from gRPC.
 */
public class JdbcWorkspacesRepository implements WorkspacesRepository {

    //language=SQL
    private static final String SELECT_ALL =
            "SELECT * FROM workspaces";

    //language=SQL
    private static final String SELECT_BY_ID =
            "SELECT * FROM workspaces WHERE workspace_id = :workspace_id";

    //language=SQL
    private static final String INSERT = """
            INSERT INTO workspaces (workspace_id, hostname, port, plaintext)
            VALUES (:workspace_id, :hostname, :port, :plaintext)""";

    private final DatabaseClient databaseClient;

    public JdbcWorkspacesRepository(DatabaseClientProvider databaseClientProvider) {
        this.databaseClient = databaseClientProvider.provide(GroupLabel.WORKSPACES);
    }

    @Override
    public List<RemoteWorkspaceInfo> findAll() {
        return databaseClient.query(
                StatementLabel.FIND_ALL_WORKSPACES,
                SELECT_ALL,
                new MapSqlParameterSource(),
                connectionMapper());
    }

    @Override
    public Optional<RemoteWorkspaceInfo> find(String workspaceId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("workspace_id", workspaceId);

        return databaseClient.querySingle(
                StatementLabel.FIND_WORKSPACE_BY_ID, SELECT_BY_ID, params, connectionMapper());
    }

    @Override
    public RemoteWorkspaceInfo create(RemoteWorkspaceInfo workspaceInfo) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("workspace_id", workspaceInfo.id())
                .addValue("hostname", workspaceInfo.address().hostname())
                .addValue("port", workspaceInfo.address().port())
                .addValue("plaintext", workspaceInfo.address().plaintext());

        databaseClient.update(StatementLabel.INSERT_WORKSPACE, INSERT, params);
        return workspaceInfo;
    }

    /**
     * Maps the minimal workspaces row to a RemoteWorkspaceInfo with only connection fields populated.
     * Fields like name, description, status, projectCount will be filled by gRPC resolveInfo().
     */
    private static RowMapper<RemoteWorkspaceInfo> connectionMapper() {
        return (rs, _) -> {
            String workspaceId = rs.getString("workspace_id");
            return new RemoteWorkspaceInfo(
                    workspaceId,
                    workspaceId,
                    null,
                    new WorkspaceAddress(rs.getString("hostname"), rs.getInt("port"), rs.getBoolean("plaintext")),
                    Instant.EPOCH,
                    WorkspaceStatus.UNKNOWN,
                    0);
        };
    }
}
