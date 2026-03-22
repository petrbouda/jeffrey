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

package pbouda.jeffrey.provider.profile.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import pbouda.jeffrey.provider.profile.repository.ProfileInfoRepository;
import pbouda.jeffrey.shared.persistence.GroupLabel;
import pbouda.jeffrey.shared.persistence.StatementLabel;
import pbouda.jeffrey.shared.persistence.client.DatabaseClient;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;

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
