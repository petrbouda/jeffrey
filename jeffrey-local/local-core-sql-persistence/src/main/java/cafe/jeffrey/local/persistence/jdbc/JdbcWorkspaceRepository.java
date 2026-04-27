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

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.util.List;

public class JdbcWorkspaceRepository implements WorkspaceRepository {

    //language=SQL
    private static final String SELECT_PROFILE_IDS =
            "SELECT profile_id FROM profiles WHERE workspace_id = :workspace_id";

    //language=SQL
    private static final String DELETE_WORKSPACE_CASCADE = """
            DELETE FROM recording_groups WHERE project_id IN (SELECT DISTINCT project_id FROM profiles WHERE workspace_id = '%workspace_id%');
            DELETE FROM recording_files WHERE project_id IN (SELECT DISTINCT project_id FROM profiles WHERE workspace_id = '%workspace_id%');
            DELETE FROM recordings WHERE project_id IN (SELECT DISTINCT project_id FROM profiles WHERE workspace_id = '%workspace_id%');
            DELETE FROM profiler_settings WHERE workspace_id = '%workspace_id%';
            DELETE FROM profiles WHERE workspace_id = '%workspace_id%';
            DELETE FROM workspaces WHERE workspace_id = '%workspace_id%'""";

    private final String workspaceId;
    private final DatabaseClient databaseClient;

    public JdbcWorkspaceRepository(String workspaceId, DatabaseClientProvider databaseClientProvider) {
        this.workspaceId = workspaceId;
        this.databaseClient = databaseClientProvider.provide(GroupLabel.WORKSPACES);
    }

    @Override
    public List<String> delete() {
        // Collect profile IDs before deletion for filesystem cleanup
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("workspace_id", workspaceId);

        List<String> profileIds = databaseClient.query(
                StatementLabel.FIND_ALL_PROFILES, SELECT_PROFILE_IDS, params,
                (rs, _) -> rs.getString("profile_id"));

        String sql = DELETE_WORKSPACE_CASCADE.replaceAll("%workspace_id%", workspaceId);
        databaseClient.delete(StatementLabel.DELETE_WORKSPACE, sql);

        return profileIds;
    }
}
