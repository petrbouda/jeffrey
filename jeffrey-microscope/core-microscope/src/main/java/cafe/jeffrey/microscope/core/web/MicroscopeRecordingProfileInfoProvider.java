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

package cafe.jeffrey.microscope.core.web;

import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.shared.common.model.Recording;
import cafe.jeffrey.shared.ui.workspace.bridge.RecordingProfileInfoProvider;

/**
 * Microscope's {@link RecordingProfileInfoProvider}: resolves the profile size/modified info for a
 * recording that has an associated analysis profile, via the microscope {@link RecordingsManager}.
 */
public class MicroscopeRecordingProfileInfoProvider implements RecordingProfileInfoProvider {

    private final RecordingsManager recordingsManager;

    public MicroscopeRecordingProfileInfoProvider(RecordingsManager recordingsManager) {
        this.recordingsManager = recordingsManager;
    }

    @Override
    public ProfileInfo profileInfo(Recording recording) {
        if (!recording.hasProfile()) {
            return ProfileInfo.NONE;
        }
        ProfileManager profileManager = recordingsManager.profile(recording.profileId()).orElse(null);
        if (profileManager == null) {
            return ProfileInfo.NONE;
        }
        return new ProfileInfo(
                profileManager.sizeInBytes(),
                profileManager.info().modified(),
                profileManager.info().createdAt().toEpochMilli());
    }
}
