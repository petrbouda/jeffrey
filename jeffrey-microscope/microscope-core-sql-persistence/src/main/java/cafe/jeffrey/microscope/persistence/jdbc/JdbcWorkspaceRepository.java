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

package cafe.jeffrey.microscope.persistence.jdbc;

import cafe.jeffrey.microscope.persistence.api.*;

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
            DELETE FROM profiles WHERE workspace_id = '%workspace_id%'""";

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
