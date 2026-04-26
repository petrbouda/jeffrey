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

package cafe.jeffrey.local.core.web.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.local.core.manager.project.ProjectManager;
import cafe.jeffrey.local.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.local.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.local.core.resources.response.ProfileWithContextResponse;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.shared.common.InstantUtils;
import cafe.jeffrey.shared.common.model.ProfileInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Top-level GET /api/internal/profiles. Returns all profiles across all
 * workspaces and projects (with workspace/project context).
 */
@RestController
@RequestMapping("/api/internal/profiles")
public class ProfilesController {

    private static final Logger LOG = LoggerFactory.getLogger(ProfilesController.class);

    private final WorkspacesManager workspacesManager;

    public ProfilesController(WorkspacesManager workspacesManager) {
        this.workspacesManager = workspacesManager;
    }

    @GetMapping
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

    private static ProfileWithContextResponse toResponse(
            ProfileManager profileManager,
            String workspaceName,
            String projectName) {
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
                profileInfo.modified(),
                profileInfo.duration().toMillis(),
                profileManager.sizeInBytes());
    }
}
