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

package pbouda.jeffrey.local.core.resources;

import jakarta.ws.rs.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.local.core.manager.qanalysis.QuickAnalysisManager;
import pbouda.jeffrey.local.core.manager.project.ProjectManager;
import pbouda.jeffrey.local.core.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.local.core.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.local.core.resources.response.ProfileWithContextResponse;
import pbouda.jeffrey.local.persistence.repository.LocalCoreRepositories;
import pbouda.jeffrey.shared.common.InstantUtils;
import pbouda.jeffrey.profile.manager.ProfileManager;
import pbouda.jeffrey.profile.resources.ProfileDiffResource;
import pbouda.jeffrey.profile.resources.ProfileResource;
import pbouda.jeffrey.profile.resources.ProfileResourceFactory;
import pbouda.jeffrey.shared.common.model.ProfileInfo;

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

    private static final Logger LOG = LoggerFactory.getLogger(ProfilesResource.class);

    private final WorkspacesManager workspacesManager;
    private final QuickAnalysisManager quickAnalysisManager;
    private final LocalCoreRepositories localCoreRepositories;
    private final ProfileResourceFactory profileResourceFactory;

    public ProfilesResource(
            WorkspacesManager workspacesManager,
            QuickAnalysisManager quickAnalysisManager,
            LocalCoreRepositories localCoreRepositories,
            ProfileResourceFactory profileResourceFactory) {
        this.workspacesManager = workspacesManager;
        this.quickAnalysisManager = quickAnalysisManager;
        this.localCoreRepositories = localCoreRepositories;
        this.profileResourceFactory = profileResourceFactory;
    }

    /**
     * Returns all profiles across all workspaces and projects.
     */
    @GET
    public List<ProfileWithContextResponse> listAllProfiles() {
        List<ProfileWithContextResponse> allProfiles = new ArrayList<>();

        for (WorkspaceManager workspaceManager : workspacesManager.findAll()) {
            String workspaceName = workspaceManager.resolveInfo().name();
            for (ProjectManager projectManager : workspaceManager.projectsManager().findAll()) {
                String projectName = projectManager.info().name();
                for (ProfileManager profileManager : projectManager.profilesManager().allProfiles()) {
                    allProfiles.add(toResponse(profileManager, workspaceName, projectName));
                }
            }
        }

        var result = allProfiles.stream()
                .sorted(Comparator.comparing(ProfileWithContextResponse::createdAt).reversed())
                .toList();
        LOG.debug("Listed all profiles across workspaces: count={}", result.size());
        return result;
    }

    /**
     * Access a single profile by its ID.
     */
    @Path("/{profileId}")
    public ProfileResource profileResource(@PathParam("profileId") String profileId) {
        ProfileManager profileManager = findProfileManager(profileId)
                .orElseThrow(() -> new NotFoundException("Profile not found: " + profileId));
        return profileResourceFactory.create(profileManager);
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
     * Finds a profile manager by profile ID using direct DB lookup.
     * Profiles are always stored locally, so we avoid iterating all workspaces/projects
     * (which would trigger HTTP calls for remote workspaces).
     */
    private Optional<ProfileManager> findProfileManager(String profileId) {
        Optional<ProfileManager> quickProfile = quickAnalysisManager.profile(profileId);
        if (quickProfile.isPresent()) {
            return quickProfile;
        }

        Optional<ProfileInfo> profileInfoOpt = localCoreRepositories.newProfileRepository(profileId).find();
        if (profileInfoOpt.isEmpty()) {
            return Optional.empty();
        }

        ProfileInfo profileInfo = profileInfoOpt.get();
        return workspacesManager.findById(profileInfo.workspaceId())
                .flatMap(ws -> ws.projectsManager().project(profileInfo.projectId()))
                .flatMap(pm -> pm.profilesManager().profile(profileId));
    }

    private static ProfileWithContextResponse toResponse(ProfileManager profileManager, String workspaceName, String projectName) {
        ProfileInfo profileInfo = profileManager.info();
        return new ProfileWithContextResponse(
                profileInfo.id(),
                profileInfo.name(),
                profileInfo.projectId(),
                projectName,
                profileInfo.workspaceId(),
                workspaceName,
                InstantUtils.formatInstant(profileInfo.createdAt()),
                profileInfo.eventSource(),
                profileInfo.enabled(),
                profileInfo.duration().toMillis(),
                profileManager.sizeInBytes());
    }
}
