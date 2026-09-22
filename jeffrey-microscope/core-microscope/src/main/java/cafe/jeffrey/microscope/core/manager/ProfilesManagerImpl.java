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
