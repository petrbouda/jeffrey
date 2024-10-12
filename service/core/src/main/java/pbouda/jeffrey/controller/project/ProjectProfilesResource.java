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

package pbouda.jeffrey.controller.project;

import jakarta.ws.rs.*;
import pbouda.jeffrey.controller.project.profile.ProfileResource;
import pbouda.jeffrey.controller.request.CreateProfileRequest;
import pbouda.jeffrey.manager.ProfileManager;
import pbouda.jeffrey.manager.ProfilesManager;
import pbouda.jeffrey.repository.model.ProfileInfo;

import java.util.Comparator;
import java.util.List;

public class ProjectProfilesResource {

    private final ProfilesManager profilesManager;

    public ProjectProfilesResource(ProfilesManager profilesManager) {
        this.profilesManager = profilesManager;
    }

    @Path("/{profileId}")
    public ProfileResource profileResource(@PathParam("profileId") String profileId) {
        ProfileManager profileManager = profilesManager.profile(profileId)
                .orElseThrow(() -> new NotFoundException("Profile not found"));
        return new ProfileResource(profileManager);
    }

    @Path("/{primaryProfileId}/diff/{secondaryProfileId}")
    public ProfileResource profileResource(
            @PathParam("primaryProfileId") String primaryProfileId,
            @PathParam("secondaryProfileId") String secondaryProfileId) {

        ProfileManager primaryProfileManager = profilesManager.profile(primaryProfileId)
                .orElseThrow(() -> new NotFoundException("Primary profile not found"));
        ProfileManager secondaryProfileManager = profilesManager.profile(secondaryProfileId)
                .orElseThrow(() -> new NotFoundException("Secondary profile not found"));

        return new ProfileResource(primaryProfileManager, secondaryProfileManager);
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
