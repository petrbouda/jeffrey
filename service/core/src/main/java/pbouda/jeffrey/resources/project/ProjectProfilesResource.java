/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.resources.project;

import jakarta.ws.rs.*;
import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.manager.ProfileManager;
import pbouda.jeffrey.manager.ProfilesManager;
import pbouda.jeffrey.manager.ProjectManager;
import pbouda.jeffrey.manager.ProjectsManager;
import pbouda.jeffrey.resources.project.profile.ProfileDiffResource;
import pbouda.jeffrey.resources.project.profile.ProfileResource;
import pbouda.jeffrey.resources.request.CreateProfileRequest;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class ProjectProfilesResource {

    private final ProfilesManager profilesManager;
    private final ProjectsManager projectsManager;

    /**
     * @param profilesManager Primary Profiles Manager
     * @param projectsManager Projects Manager to retrieve Profiles from different Projects
     */
    public ProjectProfilesResource(ProfilesManager profilesManager, ProjectsManager projectsManager) {
        this.profilesManager = profilesManager;
        this.projectsManager = projectsManager;
    }

    @Path("/{profileId}")
    public ProfileResource profileResource(@PathParam("profileId") String profileId) {
        ProfileManager profileManager = profilesManager.profile(profileId)
                .orElseThrow(() -> new NotFoundException("Profile not found"));
        return new ProfileResource(profileManager);
    }

    @Path("/{primaryProfileId}/diff/{secondaryProfileId}")
    public ProfileDiffResource profileResource(
            @PathParam("primaryProfileId") String primaryProfileId,
            @PathParam("secondaryProfileId") String secondaryProfileId) {

        ProfileManager primaryProfileManager = profilesManager.profile(primaryProfileId)
                .orElseThrow(() -> new NotFoundException("Primary profile not found"));
        ProfileManager secondaryProfileManager = profilesManager.profile(secondaryProfileId)
                .or(() -> secondaryProfileManager(secondaryProfileId))
                .orElseThrow(() -> new NotFoundException("Secondary profile not found"));

        return new ProfileDiffResource(primaryProfileManager, secondaryProfileManager);
    }

    /**
     * Selects the Profile Manager from the different Projects to support Differential Graphs
     * across different Projects.
     *
     * @param secondaryProfileId Secondary Profile ID
     * @return Profile Manager from the different Project, or {@link Optional#empty()} if not found
     */
    private Optional<ProfileManager> secondaryProfileManager(String secondaryProfileId) {
        for (ProjectManager projectManager : projectsManager.allProjects()) {
            Optional<ProfileManager> profileManager = projectManager.profilesManager()
                    .profile(secondaryProfileId);

            if (profileManager.isPresent()) {
                return profileManager;
            }
        }
        return Optional.empty();
    }

    @GET
    public List<ProfileInfo> profiles() {
        return profilesManager.allProfiles().stream()
                .map(ProfileManager::info)
                .sorted(Comparator.comparing(ProfileInfo::createdAt).reversed())
                .toList();
    }

    @POST
    public ProfileInfo createProfile(CreateProfileRequest request) {
        return profilesManager.createProfile(java.nio.file.Path.of(request.recordingPath())).info();
    }
}
