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

package cafe.jeffrey.microscope.persistence.api;

import cafe.jeffrey.microscope.model.ProfileInfo;

import java.util.List;

/**
 * Factory interface for platform-level repositories shared between local and server deployments.
 * Provides access to repositories for profiles and recordings.
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

    List<ProfileInfo> findAllProfilesByProject(String projectId);

    /**
     * Every profile this microscope knows about, whichever project or workspace it belongs to and
     * including the Quick Analysis ones that belong to none.
     * <p>
     * {@link #findAllProfilesByProject} cannot answer this: it matches {@code project_id = :project_id},
     * and a Quick Analysis profile stores a null there, which no equality comparison matches.
     */
    List<ProfileInfo> findAllProfiles();
}
