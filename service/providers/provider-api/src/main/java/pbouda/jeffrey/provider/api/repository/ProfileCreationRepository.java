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

package pbouda.jeffrey.provider.api.repository;

import pbouda.jeffrey.shared.model.RecordingEventSource;

import java.time.Instant;

/**
 * Repository for creating and initializing profiles.
 * This is used during the profile creation flow to persist profile metadata.
 */
public interface ProfileCreationRepository {

    /**
     * Insert a new profile record. The profile is created as disabled and not initialized.
     * After all events are parsed, call {@link #initializeProfile(String)} to mark as initialized.
     */
    void insertProfile(InsertProfile profile);

    /**
     * Mark the profile as initialized after all events have been parsed and stored.
     */
    void initializeProfile(String profileId);

    /**
     * Update the recording finished timestamp based on the latest event timestamp.
     */
    void updateFinishedAtTimestamp(String profileId);

    record InsertProfile(
            String projectId,
            String profileId,
            String profileName,
            RecordingEventSource eventSource,
            Instant createdAt,
            String recordingId,
            Instant recordingStartedAt,
            Instant recordingFinishedAt) {
    }

    @FunctionalInterface
    interface Factory {
        ProfileCreationRepository create();
    }
}
