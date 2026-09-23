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

package cafe.jeffrey.microscope.persistence.api;

import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.RecordingEventSource;

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
     * Insert a new profile record. The profile is created as disabled.
     *
     * @param profile the profile data to insert
     */
    void insert(InsertProfile profile);

    /**
     * Newly created Profile is disabled by default. We need to explicitly call to enabled it after all
     * post-creation activities (caching etc.). After enabling, the profile is ready to be used by the system.
     *
     * @param enabledAt the timestamp when the profile is enabled
     */
    void enableProfile(Instant enabledAt);

    /**
     * Update the profile name.
     *
     * @param name the new name for the profile
     * @return the updated profile info
     */
    ProfileInfo update(String name);

    /**
     * Marks the profile as modified by tools (rename frames, collapse frames, etc.).
     */
    void markModified();

    /**
     * Delete the profile metadata from the platform database.
     */
    void delete();

    /**
     * Data required to insert a new profile record. Every profile the product creates comes from a
     * recording and belongs to no project; project and workspace ids exist only on rows an older
     * build left behind.
     */
    record InsertProfile(
            String profileName,
            RecordingEventSource eventSource,
            Instant createdAt,
            String recordingId,
            Instant recordingStartedAt,
            Instant recordingFinishedAt) {
    }
}
