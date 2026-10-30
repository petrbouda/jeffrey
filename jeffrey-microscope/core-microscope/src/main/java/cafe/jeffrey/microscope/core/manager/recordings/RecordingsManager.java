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

import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.profile.common.pipeline.PipelineProgress;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.recordings.core.manager.RecordingsCoreManager;
import cafe.jeffrey.storage.recording.api.file.Recording;

import java.util.List;
import java.util.Optional;

/**
 * Microscope's recording manager: the deployment-agnostic store operations (inherited from
 * {@link RecordingsCoreManager}) plus the profile-creation / profile-lifecycle operations that
 * only the full microscope deployment provides.
 */
public interface RecordingsManager extends RecordingsCoreManager {

    // Profile operations

    /**
     * Builds the recording's first profile, or hands back the newest one it has: a run in progress is
     * joined, a built profile kept, and a failed attempt dropped and built again.
     */
    String analyzeRecording(String recordingId);

    /**
     * Builds another profile from the recording beside the ones it already has, which stay as they
     * are; a run of it still in progress is joined instead.
     */
    String initializeAgain(String recordingId);

    /** Every profile built from the recording, newest first. */
    List<ProfileInfo> profiles(String recordingId);

    void updateProfileName(String profileId, String profileName);

    /**
     * Deletes one of the recording's profiles and leaves the recording and its other profiles in place.
     * A profile that is not the recording's is refused as an invalid request.
     */
    void deleteProfile(String recordingId, String profileId);

    Optional<ProfileManager> profile(String profileId);

    /**
     * How far one of the recording's profiles has got through its initialization, from the pipeline of
     * the recording's kind; idle when no run is known for it.
     */
    PipelineProgress initProgress(Recording recording, String profileId);
}
