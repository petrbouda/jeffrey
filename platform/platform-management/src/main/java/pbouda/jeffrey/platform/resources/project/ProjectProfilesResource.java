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

package pbouda.jeffrey.platform.resources.project;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import pbouda.jeffrey.profile.ai.service.HeapDumpContextExtractor;
import pbouda.jeffrey.profile.ai.service.OqlAssistantService;
import pbouda.jeffrey.shared.common.model.ProfileInfo;
import pbouda.jeffrey.shared.common.model.RecordingEventSource;
import pbouda.jeffrey.profile.manager.ProfileManager;
import pbouda.jeffrey.platform.manager.ProfilesManager;
import pbouda.jeffrey.platform.manager.project.ProjectManager;
import pbouda.jeffrey.platform.manager.project.ProjectsManager;
import pbouda.jeffrey.profile.resources.ProfileDiffResource;
import pbouda.jeffrey.profile.resources.ProfileResource;
import pbouda.jeffrey.platform.resources.request.CreateProfileRequest;
import pbouda.jeffrey.platform.resources.util.InstantUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class ProjectProfilesResource {

    public record ProfileResponse(
            String id,
            String name,
            String createdAt,
            RecordingEventSource eventSource,
            boolean enabled,
            long durationInMillis,
            long sizeInBytes) {
    }

    private final ProfilesManager profilesManager;
    private final ProjectsManager projectsManager;
    private final OqlAssistantService oqlAssistantService;
    private final HeapDumpContextExtractor heapDumpContextExtractor;

    /**
     * @param profilesManager          Primary Profiles Manager
     * @param projectsManager          Projects Manager to retrieve Profiles from different Projects
     * @param oqlAssistantService      AI-powered OQL assistant service
     * @param heapDumpContextExtractor Extracts heap dump context for AI prompts
     */
    public ProjectProfilesResource(
            ProfilesManager profilesManager,
            ProjectsManager projectsManager,
            OqlAssistantService oqlAssistantService,
            HeapDumpContextExtractor heapDumpContextExtractor) {
        this.profilesManager = profilesManager;
        this.projectsManager = projectsManager;
        this.oqlAssistantService = oqlAssistantService;
        this.heapDumpContextExtractor = heapDumpContextExtractor;
    }

    @Path("/{profileId}")
    public ProfileResource profileResource(@PathParam("profileId") String profileId) {
        ProfileManager profileManager = profilesManager.profile(profileId)
                .orElseThrow(() -> new NotFoundException("Profile not found"));
        return new ProfileResource(profileManager, oqlAssistantService, heapDumpContextExtractor);
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
    public List<ProfileResponse> profiles() {
        return profilesManager.allProfiles().stream()
                .sorted(Comparator.comparing((ProfileManager pm) -> pm.info().createdAt()).reversed())
                .map(ProjectProfilesResource::toResponse)
                .toList();
    }

    private static ProfileResponse toResponse(ProfileManager profileManager) {
        ProfileInfo profileInfo = profileManager.info();
        return new ProfileResponse(
                profileInfo.id(),
                profileInfo.name(),
                InstantUtils.formatInstant(profileInfo.createdAt()),
                profileInfo.eventSource(),
                profileInfo.enabled(),
                profileInfo.duration().toMillis(),
                profileManager.sizeInBytes());
    }

    @POST
    public Response createProfile(CreateProfileRequest request) {
        profilesManager.createProfile(request.recordingId());
        return Response.accepted().build();
    }
}
