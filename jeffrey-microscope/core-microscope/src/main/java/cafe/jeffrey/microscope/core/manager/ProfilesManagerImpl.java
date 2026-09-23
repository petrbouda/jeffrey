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

package cafe.jeffrey.microscope.core.manager;

import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.microscope.model.ProjectInfo;

import java.util.List;
import java.util.Optional;

/**
 * Reads the profiles that belong to one project.
 *
 * <p>Read-only, because nothing writes a profile into a project any more: every profile the
 * product creates goes through the recordings path, which stores it with no project at all. What
 * this answers for is the rows an older build left behind.
 */
public class ProfilesManagerImpl implements ProfilesManager {

    private final ProjectInfo projectInfo;
    private final MicroscopeCoreRepositories localCoreRepositories;
    private final ProfileManager.Factory profileManagerFactory;

    public ProfilesManagerImpl(
            ProjectInfo projectInfo,
            MicroscopeCoreRepositories localCoreRepositories,
            ProfileManager.Factory profileManagerFactory) {

        this.projectInfo = projectInfo;
        this.localCoreRepositories = localCoreRepositories;
        this.profileManagerFactory = profileManagerFactory;
    }

    @Override
    public List<? extends ProfileManager> allProfiles() {
        return localCoreRepositories.findAllProfilesByProject(projectInfo.id()).stream()
                .map(profileManagerFactory)
                .toList();
    }

    @Override
    public Optional<ProfileManager> profile(String profileId) {
        return localCoreRepositories.newProfileRepository(profileId).find()
                .map(profileManagerFactory);
    }
}
