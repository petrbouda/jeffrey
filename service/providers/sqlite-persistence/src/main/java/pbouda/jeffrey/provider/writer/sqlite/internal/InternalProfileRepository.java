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

package pbouda.jeffrey.provider.writer.sqlite.internal;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import pbouda.jeffrey.common.model.EventFieldsSetting;
import pbouda.jeffrey.common.model.EventSource;
import pbouda.jeffrey.provider.writer.sqlite.client.DatabaseClient;

import javax.sql.DataSource;
import java.time.Instant;

public class InternalProfileRepository {

    public record InsertProfile(
            String projectId,
            String profileId,
            String profileName,
            EventSource eventSource,
            EventFieldsSetting eventFieldsSetting,
            Instant createdAt,
            String recordingId,
            Instant recordingStartedAt,
            Instant recordingFinishedAt) {
    }

    //language=SQL
    private static final String INSERT_PROFILE = """
            INSERT INTO profiles (
                 profile_id,
                 project_id,
                 profile_name,
                 event_source,
                 event_fields_setting,
                 created_at,
                 recording_id,
                 recording_started_at,
                 recording_finished_at)
            
                VALUES (:profile_id,
                        :project_id,
                        :profile_name,
                        :event_source,
                        :event_fields_setting,
                        :created_at,
                        :recording_id,
                        :recording_started_at,
                        :recording_finished_at)""";

    private static final String INITIALIZE_PROFILE = """
            UPDATE profiles
                SET initialized_at = :initialized_at
                WHERE profile_id = :profile_id""";

    private final DatabaseClient databaseClient;

    public InternalProfileRepository(DataSource dataSource) {
        this.databaseClient = new DatabaseClient(dataSource, "internal-profiles");
    }

    /**
     * Insert a new profile. The profile must have a unique ID. The profile is inserted as disabled and not initialized.
     * Initialize -> The profile is initialized when the all events are inserted
     * Enabled -> The profile is enabled when all operation after the initialization is done (caching etc.)
     * We need to call {@link #initializeProfile(String)} to finish initialization.
     *
     * @param profile the profile to insert
     */
    public void insertProfile(InsertProfile profile) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("profile_id", profile.profileId())
                .addValue("project_id", profile.projectId())
                .addValue("profile_name", profile.profileName())
                .addValue("event_source", profile.eventSource().name())
                .addValue("event_fields_setting", profile.eventFieldsSetting().name())
                .addValue("created_at", profile.createdAt().toEpochMilli())
                .addValue("recording_id", profile.recordingId())
                .addValue("recording_started_at", profile.recordingStartedAt().toEpochMilli())
                .addValue("recording_finished_at", profile.recordingFinishedAt().toEpochMilli());

        databaseClient.insert(INSERT_PROFILE, params);
    }

    /**
     * Finish initialization of the profile. The profile is still not enabled after this operation.
     *
     * @param profileId the ID of the profile to finish the initialization.
     */
    public void initializeProfile(String profileId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("profile_id", profileId)
                .addValue("initialized_at", Instant.now().toEpochMilli());

        databaseClient.update(INITIALIZE_PROFILE, params);
    }

    /**
     * Retrieve the latest event timestamp for a given profile.
     *
     * @param profileId the ID of the profile to get the latest event timestamp for
     */
    public void updateFinishedAtTimestamp(String profileId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("profile_id", profileId);

        String sql = "SELECT MAX(timestamp) FROM events WHERE profile_id = :profile_id";
        long latestTimestamp = databaseClient.queryLong(sql, params);

        MapSqlParameterSource updateParams = new MapSqlParameterSource()
                .addValue("profile_id", profileId)
                .addValue("finished_at", latestTimestamp);

        String updateSql = "UPDATE profiles SET recording_finished_at = :finished_at WHERE profile_id = :profile_id";
        databaseClient.update(updateSql, updateParams);
    }
}
