/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.microscope.core.manager.recordings;

import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.recordings.core.manager.RecordingsCoreManager;

import java.util.Optional;

/**
 * Microscope's recording manager: the deployment-agnostic store operations (inherited from
 * {@link RecordingsCoreManager}) plus the profile-creation / profile-lifecycle operations that
 * only the full microscope deployment provides.
 */
public interface RecordingsManager extends RecordingsCoreManager {

    // Profile operations
    String analyzeRecording(String recordingId);

    void updateProfileName(String profileId, String profileName);

    void deleteProfile(String recordingId);

    Optional<ProfileManager> profile(String profileId);
}
