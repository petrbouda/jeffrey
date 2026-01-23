/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.platform.resources;

import jakarta.ws.rs.*;
import pbouda.jeffrey.platform.manager.project.ProjectManager;
import pbouda.jeffrey.platform.manager.workspace.CompositeWorkspacesManager;
import pbouda.jeffrey.platform.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.platform.resources.util.InstantUtils;
import pbouda.jeffrey.profile.ai.service.HeapDumpContextExtractor;
import pbouda.jeffrey.profile.ai.service.OqlAssistantService;
import pbouda.jeffrey.profile.manager.ProfileManager;
import pbouda.jeffrey.profile.resources.ProfileDiffResource;
import pbouda.jeffrey.profile.resources.ProfileResource;
import pbouda.jeffrey.shared.common.model.ProfileInfo;
import pbouda.jeffrey.shared.common.model.RecordingEventSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Root-level profiles resource that allows accessing profiles by profileId alone,
 * without requiring workspaceId and projectId in the URL path.
 * <p>
 * This enables simplified URLs like /api/internal/profiles/{profileId}/...
 * instead of /api/internal/workspaces/{wsId}/projects/{pId}/profiles/{profileId}/...
 */
public class ProfilesResource {

    /**
     * Profile response with workspace and project context.
     */
    public record ProfileResponse(
            String id,
            String name,
            String projectId,
            String workspaceId,
            String createdAt,
            RecordingEventSource eventSource,
            boolean enabled,
            long durationInMillis,
            long sizeInBytes) {
    }

    private final CompositeWorkspacesManager workspacesManager;
    private final OqlAssistantService oqlAssistantService;
    private final HeapDumpContextExtractor heapDumpContextExtractor;

    public ProfilesResource(
            CompositeWorkspacesManager workspacesManager,
            OqlAssistantService oqlAssistantService,
            HeapDumpContextExtractor heapDumpContextExtractor) {
        this.workspacesManager = workspacesManager;
        this.oqlAssistantService = oqlAssistantService;
        this.heapDumpContextExtractor = heapDumpContextExtractor;
    }

    /**
     * Returns all profiles across all workspaces and projects.
     */
    @GET
    public List<ProfileResponse> listAllProfiles() {
        List<ProfileResponse> allProfiles = new ArrayList<>();

        for (WorkspaceManager workspaceManager : workspacesManager.findAll()) {
            for (ProjectManager projectManager : workspaceManager.projectsManager().findAll()) {
                for (ProfileManager profileManager : projectManager.profilesManager().allProfiles()) {
                    allProfiles.add(toResponse(profileManager));
                }
            }
        }

        return allProfiles.stream()
                .sorted(Comparator.comparing(ProfileResponse::createdAt).reversed())
                .toList();
    }

    /**
     * Access a single profile by its ID.
     */
    @Path("/{profileId}")
    public ProfileResource profileResource(@PathParam("profileId") String profileId) {
        ProfileManager profileManager = findProfileManager(profileId)
                .orElseThrow(() -> new NotFoundException("Profile not found: " + profileId));
        return new ProfileResource(profileManager, oqlAssistantService, heapDumpContextExtractor);
    }

    /**
     * Access differential analysis between two profiles.
     */
    @Path("/{primaryProfileId}/diff/{secondaryProfileId}")
    public ProfileDiffResource profileDiffResource(
            @PathParam("primaryProfileId") String primaryProfileId,
            @PathParam("secondaryProfileId") String secondaryProfileId) {

        ProfileManager primaryProfileManager = findProfileManager(primaryProfileId)
                .orElseThrow(() -> new NotFoundException("Primary profile not found: " + primaryProfileId));
        ProfileManager secondaryProfileManager = findProfileManager(secondaryProfileId)
                .orElseThrow(() -> new NotFoundException("Secondary profile not found: " + secondaryProfileId));

        return new ProfileDiffResource(primaryProfileManager, secondaryProfileManager);
    }

    /**
     * Finds a profile manager by profile ID across all workspaces and projects.
     */
    private Optional<ProfileManager> findProfileManager(String profileId) {
        for (WorkspaceManager workspaceManager : workspacesManager.findAll()) {
            for (ProjectManager projectManager : workspaceManager.projectsManager().findAll()) {
                Optional<ProfileManager> profileManager = projectManager.profilesManager().profile(profileId);
                if (profileManager.isPresent()) {
                    return profileManager;
                }
            }
        }
        return Optional.empty();
    }

    private static ProfileResponse toResponse(ProfileManager profileManager) {
        ProfileInfo profileInfo = profileManager.info();
        return new ProfileResponse(
                profileInfo.id(),
                profileInfo.name(),
                profileInfo.projectId(),
                profileInfo.workspaceId(),
                InstantUtils.formatInstant(profileInfo.createdAt()),
                profileInfo.eventSource(),
                profileInfo.enabled(),
                profileInfo.duration().toMillis(),
                profileManager.sizeInBytes());
    }
}
