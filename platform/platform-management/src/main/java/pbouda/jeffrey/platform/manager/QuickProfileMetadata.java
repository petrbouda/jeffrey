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

package pbouda.jeffrey.platform.manager;

import pbouda.jeffrey.shared.common.model.ProfileInfo;
import pbouda.jeffrey.shared.common.model.RecordingEventSource;

import java.nio.file.Path;
import java.time.Instant;

/**
 * Metadata for quick analysis profiles stored as JSON on the filesystem.
 * This allows profiles to be resolved from disk instead of being kept in memory.
 */
public record QuickProfileMetadata(
        String profileId,
        String profileName,
        String eventSource,
        Instant profilingStartedAt,
        Instant profilingFinishedAt,
        Instant createdAt) {

    private static final String METADATA_FILE = "profile-metadata.json";

    /**
     * Returns the path to the metadata file for a given profile directory.
     */
    public static Path metadataPath(Path profileDir) {
        return profileDir.resolve(METADATA_FILE);
    }

    /**
     * Converts this metadata to a ProfileInfo instance.
     * Quick analysis profiles have null workspace and project IDs.
     */
    public ProfileInfo toProfileInfo() {
        return new ProfileInfo(
                profileId,
                null,
                null,
                profileName,
                RecordingEventSource.valueOf(eventSource),
                profilingStartedAt,
                profilingFinishedAt,
                createdAt,
                true);
    }
}
