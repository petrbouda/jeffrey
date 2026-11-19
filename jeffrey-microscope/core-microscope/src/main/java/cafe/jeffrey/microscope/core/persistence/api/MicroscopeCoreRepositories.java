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

package cafe.jeffrey.microscope.core.persistence.api;

import cafe.jeffrey.microscope.core.profile.model.ProfileInfo;

import java.util.List;

/**
 * The repositories over Microscope's core database: its recordings, their tags, the profiles built
 * from them and the IDE targets linked to those profiles.
 */
public interface MicroscopeCoreRepositories {

    ProfileRepository newProfileRepository(String profileId);

    /**
     * The recordings this microscope stores. They belong to no project: every recording arrives
     * through the recordings path, which writes it with a null project id.
     */
    RecordingRepository newRecordingRepository();

    RecordingTagsRepository recordingTagsRepository();

    IdeTargetsRepository ideTargetsRepository();

    /**
     * Every profile this microscope knows about, newest first.
     */
    List<ProfileInfo> findAllProfiles();

    /**
     * Every profile built from one recording, newest first. A recording can be initialized again,
     * and each initialization is a profile of its own; deleting the recording takes all of them.
     */
    List<ProfileInfo> findProfilesByRecording(String recordingId);
}
