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

package cafe.jeffrey.microscope.core.web;

import cafe.jeffrey.storage.recording.api.file.Recording;

import java.util.List;

/**
 * Enriches the recordings list with the profiles built from each recording and the fields
 * ({@code sizeInBytes} / {@code modified} / init progress) only the profile managers can compute,
 * so {@code RecordingsController} stays free of the profile pipeline's types.
 */
@FunctionalInterface
public interface RecordingProfileInfoProvider {

    /**
     * Every profile built from the recording, newest first: a recording can be initialized again, and
     * each initialization is a profile of its own. Empty for a recording never initialized.
     */
    List<RecordingProfile> profiles(Recording recording);

    /**
     * @param createdAt    when the profile was created (analyzed), as epoch millis. Lets the UI sort
     *                     profiles by analysis time rather than upload time.
     * @param initProgress how far initialization has got, so the list can show a profile being built
     *                     rather than only a profile that exists
     */
    record RecordingProfile(
            String profileId,
            String profileName,
            long sizeInBytes,
            boolean modified,
            long createdAt,
            ProfileInitProgress initProgress) {

        public RecordingProfile {
            initProgress = initProgress == null ? ProfileInitProgress.NONE : initProgress;
        }
    }
}
