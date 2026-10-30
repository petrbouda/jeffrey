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

package cafe.jeffrey.shared.ui.remote.dto;

import cafe.jeffrey.shared.ui.remote.bridge.ProfileInitProgress;
import cafe.jeffrey.shared.ui.remote.bridge.RecordingProfileInfoProvider.RecordingProfile;

/**
 * One profile built from a recording, as the recordings list shows it.
 *
 * @param createdAt when the profile was created, as epoch millis
 */
public record RecordingProfileResponse(
        String profileId,
        String profileName,
        long sizeInBytes,
        boolean modified,
        long createdAt,
        ProfileInitProgress initProgress) {

    public static RecordingProfileResponse from(RecordingProfile profile) {
        return new RecordingProfileResponse(
                profile.profileId(),
                profile.profileName(),
                profile.sizeInBytes(),
                profile.modified(),
                profile.createdAt(),
                profile.initProgress());
    }
}
