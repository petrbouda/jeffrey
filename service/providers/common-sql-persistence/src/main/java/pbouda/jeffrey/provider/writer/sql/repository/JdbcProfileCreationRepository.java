/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import pbouda.jeffrey.provider.api.repository.ProfileCreationRepository;
import pbouda.jeffrey.provider.writer.sql.GroupLabel;
import pbouda.jeffrey.provider.writer.sql.StatementLabel;
import pbouda.jeffrey.provider.writer.sql.client.DatabaseClient;
import pbouda.jeffrey.provider.writer.sql.client.DatabaseClientProvider;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

public class JdbcProfileCreationRepository implements ProfileCreationRepository {

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

    private static final String INITIALIZE_PROFILE = """
            UPDATE profiles
                SET initialized_at = :initialized_at
                WHERE profile_id = :profile_id""";

    private final DatabaseClient databaseClient;
    private final Clock clock;

    public JdbcProfileCreationRepository(DatabaseClientProvider databaseClientProvider, Clock clock) {
        this.databaseClient = databaseClientProvider.provide(GroupLabel.INTERNAL_PROFILES);
        this.clock = clock;
    }

    @Override
    public void insertProfile(InsertProfile profile) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("profile_id", profile.profileId())
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
    public void initializeProfile(String profileId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("profile_id", profileId)
                .addValue("initialized_at", clock.instant().atOffset(ZoneOffset.UTC));

        databaseClient.update(StatementLabel.INITIALIZE_PROFILE, INITIALIZE_PROFILE, params);
    }

    @Override
    public void updateFinishedAtTimestamp(String profileId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("profile_id", profileId);

        String sql = "SELECT MAX(start_timestamp) AS timestamp FROM events WHERE profile_id = :profile_id";
        Optional<OffsetDateTime> latestTimestamp = databaseClient.querySingle(
                StatementLabel.MAX_EVENT_TIMESTAMP, sql, params, (rs, _) -> rs.getObject("timestamp", OffsetDateTime.class));

        if (latestTimestamp.isEmpty()) {
            throw new IllegalStateException("No events found for profile: " + profileId);
        }

        MapSqlParameterSource updateParams = new MapSqlParameterSource()
                .addValue("profile_id", profileId)
                .addValue("finished_at", latestTimestamp.get());

        String updateSql = "UPDATE profiles SET recording_finished_at = :finished_at WHERE profile_id = :profile_id";
        databaseClient.update(StatementLabel.UPDATE_PROFILE_FINISHED_AT, updateSql, updateParams);
    }
}
