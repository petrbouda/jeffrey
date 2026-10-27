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

package cafe.jeffrey.shared.ui.hub.bridge;

import cafe.jeffrey.storage.recording.api.file.Recording;

/**
 * Optional enrichment SPI for the shared recordings list. Supplies the per-recording profile
 * fields ({@code profileSizeInBytes} / {@code profileModified}) that only a profile-capable
 * deployment can compute. Deployments without profiles wire {@link #NOOP}, which reports no
 * profile data; microscope provides a real implementation backed by its profile managers.
 */
@FunctionalInterface
public interface RecordingProfileInfoProvider {

    /**
     * Resolves the profile size/modified info for a recording. Implementations should return
     * {@link ProfileInfo#NONE} when the recording has no associated profile.
     */
    ProfileInfo profileInfo(Recording recording);

    /**
     * @param profileCreatedAt when the profile was created (analyzed), as epoch millis; {@code 0} when the
     *                         recording has no profile. Lets the UI sort initialized recordings by analysis
     *                         time rather than upload time.
     * @param initProgress     how far initialization has got, so the list can show a profile being
     *                         built rather than only a profile that exists
     */
    record ProfileInfo(
            long profileSizeInBytes,
            boolean profileModified,
            long profileCreatedAt,
            ProfileInitProgress initProgress) {

        public static final ProfileInfo NONE =
                new ProfileInfo(0L, false, 0L, ProfileInitProgress.NONE);

        public ProfileInfo {
            initProgress = initProgress == null ? ProfileInitProgress.NONE : initProgress;
        }
    }

    RecordingProfileInfoProvider NOOP = recording -> ProfileInfo.NONE;
}
