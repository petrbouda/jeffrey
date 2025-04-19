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

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import pbouda.jeffrey.common.model.EventFieldsSetting;
import pbouda.jeffrey.common.model.EventSource;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Map;

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
                        :recording_finished_at)
            """;

    private static final String INITIALIZE_PROFILE = """
            UPDATE profiles
                SET initialized_at = :initialized_at
                WHERE profile_id = :profile_id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public InternalProfileRepository(DataSource dataSource) {
        jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
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
        Map<String, Object> params = Map.of(
                "profile_id", profile.profileId(),
                "project_id", profile.projectId(),
                "profile_name", profile.profileName(),
                "event_source", profile.eventSource().name(),
                "event_fields_setting", profile.eventFieldsSetting().name(),
                "created_at", profile.createdAt().toEpochMilli(),
                "recording_id", profile.recordingId(),
                "recording_started_at", profile.recordingStartedAt().toEpochMilli(),
                "recording_finished_at", profile.recordingFinishedAt().toEpochMilli()
        );

        jdbcTemplate.update(INSERT_PROFILE, params);
    }

    /**
     * Finish initialization of the profile. The profile is still not enabled after this operation.
     *
     * @param profileId the ID of the profile to finish the initialization.
     */
    public void initializeProfile(String profileId) {
        Map<String, Object> params = Map.of(
                "profile_id", profileId,
                "initialized_at", Instant.now().toEpochMilli());

        jdbcTemplate.update(INITIALIZE_PROFILE, params);
    }
}
