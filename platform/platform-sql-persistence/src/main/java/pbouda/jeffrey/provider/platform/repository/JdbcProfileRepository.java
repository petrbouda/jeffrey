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

package pbouda.jeffrey.provider.platform.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import pbouda.jeffrey.shared.common.model.ProfileInfo;
import pbouda.jeffrey.shared.persistence.GroupLabel;
import pbouda.jeffrey.shared.persistence.StatementLabel;
import pbouda.jeffrey.shared.persistence.client.DatabaseClient;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;

public class JdbcProfileRepository implements ProfileRepository {

    //language=SQL
    private static final String INSERT_PROFILE = """
            INSERT INTO profiles (
                 profile_id,
                 project_id,
                 profile_name,
                 event_source,
                 created_at,
                 recording_id,
                 recording_started_at,
                 recording_finished_at)

                VALUES (:profile_id,
                        :project_id,
                        :profile_name,
                        :event_source,
                        :created_at,
                        :recording_id,
                        :recording_started_at,
                        :recording_finished_at)""";

    //language=SQL
    private static final String INITIALIZE_PROFILE = """
            UPDATE profiles
                SET initialized_at = :initialized_at
                WHERE profile_id = :profile_id""";

    //language=SQL
    private static final String ENABLE_PROFILE =
            "UPDATE profiles SET enabled_at = :enabled_at WHERE profile_id = :profile_id";

    //language=SQL
    private static final String SELECT_SINGLE_PROFILE =
            "SELECT * FROM profiles WHERE profile_id = :profile_id";

    //language=SQL
    private static final String UPDATE_PROFILE_NAME =
            "UPDATE profiles SET profile_name = :profile_name WHERE profile_id = :profile_id";

    //language=SQL
    private static final String DELETE_PROFILE =
            "DELETE FROM profiles WHERE profile_id = :profile_id";

    private final String profileId;
    private final DatabaseClient databaseClient;
    private final Clock clock;

    public JdbcProfileRepository(String profileId, DatabaseClientProvider databaseClientProvider, Clock clock) {
        this.profileId = profileId;
        this.databaseClient = databaseClientProvider.provide(GroupLabel.PROFILES);
        this.clock = clock;
    }

    @Override
    public Optional<ProfileInfo> find() {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("profile_id", profileId);

        return databaseClient.querySingle(
                StatementLabel.FIND_PROFILE, SELECT_SINGLE_PROFILE, paramSource, Mappers.profileInfoMapper());
    }

    @Override
    public void insert(InsertProfile profile) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("profile_id", profileId)
                .addValue("project_id", profile.projectId())
                .addValue("profile_name", profile.profileName())
                .addValue("event_source", profile.eventSource().name())
                .addValue("created_at", profile.createdAt().atOffset(ZoneOffset.UTC))
                .addValue("recording_id", profile.recordingId())
                .addValue("recording_started_at", profile.recordingStartedAt().atOffset(ZoneOffset.UTC))
                .addValue("recording_finished_at", profile.recordingFinishedAt().atOffset(ZoneOffset.UTC));

        databaseClient.insert(StatementLabel.INSERT_PROFILE, INSERT_PROFILE, params);
    }

    @Override
    public void initializeProfile() {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("profile_id", profileId)
                .addValue("initialized_at", clock.instant().atOffset(ZoneOffset.UTC));

        databaseClient.update(StatementLabel.INITIALIZE_PROFILE, INITIALIZE_PROFILE, params);
    }

    @Override
    public void enableProfile() {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("profile_id", profileId)
                .addValue("enabled_at", clock.instant().atOffset(ZoneOffset.UTC));

        databaseClient.update(StatementLabel.ENABLED_PROFILE, ENABLE_PROFILE, paramSource);
    }

    @Override
    public ProfileInfo update(String name) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("profile_id", profileId)
                .addValue("profile_name", name);

        databaseClient.update(StatementLabel.UPDATE_PROFILE_NAME, UPDATE_PROFILE_NAME, paramSource);

        // Return the updated profile info
        return find().orElseThrow(() -> new RuntimeException("Profile not found after update"));
    }

    @Override
    public void delete() {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("profile_id", profileId);

        databaseClient.delete(StatementLabel.DELETE_PROFILE, DELETE_PROFILE, paramSource);
    }
}
