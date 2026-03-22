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

package pbouda.jeffrey.local.core.resources.project;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.local.core.manager.ProfilesManager;
import pbouda.jeffrey.local.core.manager.project.ProjectManager;
import pbouda.jeffrey.local.core.manager.project.ProjectsManager;
import pbouda.jeffrey.local.core.resources.request.CreateProfileRequest;
import pbouda.jeffrey.local.core.resources.response.ProfileSummaryResponse;
import pbouda.jeffrey.profile.manager.ProfileManager;
import pbouda.jeffrey.profile.resources.ProfileDiffResource;
import pbouda.jeffrey.profile.resources.ProfileResource;
import pbouda.jeffrey.profile.resources.ProfileResourceFactory;

import pbouda.jeffrey.shared.common.model.ProjectInfo;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class ProjectProfilesResource {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectProfilesResource.class);

    private final ProjectInfo projectInfo;
    private final ProfilesManager profilesManager;
    private final ProjectsManager projectsManager;
    private final ProfileResourceFactory profileResourceFactory;

    /**
     * @param projectInfo            Project info for logging context
     * @param profilesManager        Primary Profiles Manager
     * @param projectsManager        Projects Manager to retrieve Profiles from different Projects
     * @param profileResourceFactory Factory for creating ProfileResource sub-resources
     */
    public ProjectProfilesResource(
            ProjectInfo projectInfo,
            ProfilesManager profilesManager,
            ProjectsManager projectsManager,
            ProfileResourceFactory profileResourceFactory) {
        this.projectInfo = projectInfo;
        this.profilesManager = profilesManager;
        this.projectsManager = projectsManager;
        this.profileResourceFactory = profileResourceFactory;
    }

    @Path("/{profileId}")
    public ProfileResource profileResource(@PathParam("profileId") String profileId) {
        ProfileManager profileManager = profilesManager.profile(profileId)
                .orElseThrow(() -> new NotFoundException("Profile not found"));
        return profileResourceFactory.create(profileManager);
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
        for (ProjectManager projectManager : projectsManager.findAll()) {
            Optional<ProfileManager> profileManager = projectManager.profilesManager()
                    .profile(secondaryProfileId);

            if (profileManager.isPresent()) {
                return profileManager;
            }
        }
        return Optional.empty();
    }

    @GET
    public List<ProfileSummaryResponse> profiles() {
        var result = profilesManager.allProfiles().stream()
                .sorted(Comparator.comparing((ProfileManager pm) -> pm.info().createdAt()).reversed())
                .map(ProfileSummaryResponse::from)
                .toList();
        LOG.debug("Listed profiles: projectId={} count={}", projectInfo.id(), result.size());
        return result;
    }

    @POST
    public Response createProfile(CreateProfileRequest request) {
        LOG.debug("Creating profile: recordingId={}", request.recordingId());
        profilesManager.createProfile(request.recordingId());
        return Response.accepted().build();
    }
}
