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

package cafe.jeffrey.microscope.core.manager.recordings;

import cafe.jeffrey.profile.common.pipeline.PipelineProgress;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.recordings.core.manager.RecordingsCoreManager;
import cafe.jeffrey.storage.recording.api.file.Recording;

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

    /**
     * How far the profile of a recording has got through its initialization, from the pipeline of the
     * recording's kind; idle when no run is known for it.
     */
    PipelineProgress initProgress(Recording recording);
}
