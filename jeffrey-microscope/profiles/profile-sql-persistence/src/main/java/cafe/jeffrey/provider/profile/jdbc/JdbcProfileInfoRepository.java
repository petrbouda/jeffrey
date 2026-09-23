/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.provider.profile.api.*;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import cafe.jeffrey.provider.profile.api.ProfileInfoRepository;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.util.Optional;

public class JdbcProfileInfoRepository implements ProfileInfoRepository {

    //language=SQL
    private static final String INSERT_PROFILE_INFO = """
            INSERT INTO profile_info (profile_id, project_id, workspace_id)
            VALUES (:profile_id, :project_id, :workspace_id)""";

    //language=SQL
    private static final String SELECT_PROFILE_INFO =
            "SELECT profile_id, project_id, workspace_id FROM profile_info LIMIT 1";

    private final DatabaseClient databaseClient;

    public JdbcProfileInfoRepository(DatabaseClientProvider databaseClientProvider) {
        this.databaseClient = databaseClientProvider.provide(GroupLabel.PROFILES);
    }

    @Override
    public void insert(ProfileContext context) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("profile_id", context.profileId())
                .addValue("project_id", context.projectId())
                .addValue("workspace_id", context.workspaceId());

        databaseClient.insert(StatementLabel.INSERT_PROFILE_INFO, INSERT_PROFILE_INFO, params);
    }

    @Override
    public Optional<ProfileContext> find() {
        return databaseClient.querySingle(
                StatementLabel.FIND_PROFILE_INFO,
                SELECT_PROFILE_INFO,
                new MapSqlParameterSource(),
                (rs, _) -> new ProfileContext(
                        rs.getString("profile_id"),
                        rs.getString("project_id"),
                        rs.getString("workspace_id")));
    }
}
