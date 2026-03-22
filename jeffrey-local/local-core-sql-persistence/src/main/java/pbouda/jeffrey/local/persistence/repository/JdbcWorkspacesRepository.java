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

package pbouda.jeffrey.local.persistence.repository;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import pbouda.jeffrey.shared.common.IDGenerator;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceLocation;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceStatus;
import pbouda.jeffrey.shared.persistence.GroupLabel;
import pbouda.jeffrey.shared.persistence.StatementLabel;
import pbouda.jeffrey.shared.persistence.client.DatabaseClient;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Minimal local registry of remote workspace connections.
 * Only stores connection reference (workspace_id, origin_id, base_location).
 * All other workspace data (name, description, status, project count) comes from gRPC.
 */
public class JdbcWorkspacesRepository implements WorkspacesRepository {

    //language=SQL
    private static final String SELECT_ALL =
            "SELECT * FROM workspaces WHERE deleted = false";

    //language=SQL
    private static final String SELECT_BY_ID =
            "SELECT * FROM workspaces WHERE workspace_id = :workspace_id AND deleted = false";

    //language=SQL
    private static final String INSERT = """
            INSERT INTO workspaces (workspace_id, workspace_origin_id, base_location, deleted)
            VALUES (:workspace_id, :workspace_origin_id, :base_location, false)""";

    private final DatabaseClient databaseClient;

    public JdbcWorkspacesRepository(DatabaseClientProvider databaseClientProvider) {
        this.databaseClient = databaseClientProvider.provide(GroupLabel.WORKSPACES);
    }

    @Override
    public List<WorkspaceInfo> findAll() {
        return databaseClient.query(
                StatementLabel.FIND_ALL_WORKSPACES,
                SELECT_ALL,
                new MapSqlParameterSource(),
                connectionMapper());
    }

    @Override
    public Optional<WorkspaceInfo> find(String workspaceId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("workspace_id", workspaceId);

        return databaseClient.querySingle(
                StatementLabel.FIND_WORKSPACE_BY_ID, SELECT_BY_ID, params, connectionMapper());
    }

    @Override
    public WorkspaceInfo create(WorkspaceInfo workspaceInfo) {
        WorkspaceInfo newInfo = workspaceInfo.withId(IDGenerator.generate());

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("workspace_id", newInfo.id())
                .addValue("workspace_origin_id", newInfo.originId())
                .addValue("base_location", newInfo.baseLocation() != null ? newInfo.baseLocation().toString() : null);

        databaseClient.update(StatementLabel.INSERT_WORKSPACE, INSERT, params);
        return newInfo;
    }

    /**
     * Maps the minimal workspaces row to a WorkspaceInfo with only connection fields populated.
     * Fields like name, description, status, projectCount will be filled by gRPC resolveInfo().
     */
    private static RowMapper<WorkspaceInfo> connectionMapper() {
        return (rs, _) -> {
            String baseLocation = rs.getString("base_location");

            return new WorkspaceInfo(
                    rs.getString("workspace_id"),
                    rs.getString("workspace_origin_id"),
                    null,
                    null,
                    null,
                    baseLocation != null ? WorkspaceLocation.of(baseLocation) : null,
                    baseLocation != null ? WorkspaceLocation.of(baseLocation) : null,
                    Instant.EPOCH,
                    WorkspaceStatus.UNKNOWN,
                    0);
        };
    }
}
