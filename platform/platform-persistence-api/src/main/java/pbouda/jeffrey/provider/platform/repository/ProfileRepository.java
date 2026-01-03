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

import pbouda.jeffrey.shared.common.model.ProfileInfo;
import pbouda.jeffrey.shared.common.model.RecordingEventSource;

import java.time.Instant;
import java.util.Optional;

/**
 * Repository for managing profile metadata in the platform database.
 * Handles both creation and management of profile records.
 */
public interface ProfileRepository {

    /**
     * Find a single profile by its ID.
     *
     * @return the profile if it exists, otherwise an empty optional
     */
    Optional<ProfileInfo> find();

    /**
     * Insert a new profile record. The profile is created as disabled and not initialized.
     * After all events are parsed, call {@link #initializeProfile()} to mark as initialized.
     *
     * @param profile the profile data to insert
     */
    void insert(InsertProfile profile);

    /**
     * Mark the profile as initialized after all events have been parsed and stored.
     */
    void initializeProfile();

    /**
     * Newly created Profile is disabled by default. We need to explicitly call to enabled it after all
     * post-creation activities (caching etc.). After enabling, the profile is ready to be used by the system.
     */
    void enableProfile();

    /**
     * Update the profile name.
     *
     * @param name the new name for the profile
     * @return the updated profile info
     */
    ProfileInfo update(String name);

    /**
     * Delete the profile metadata from the platform database.
     */
    void delete();

    /**
     * Data required to insert a new profile record.
     */
    record InsertProfile(
            String projectId,
            String profileName,
            RecordingEventSource eventSource,
            Instant createdAt,
            String recordingId,
            Instant recordingStartedAt,
            Instant recordingFinishedAt) {
    }
}
