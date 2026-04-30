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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.local.core.manager.project.ProjectManager;
import cafe.jeffrey.local.core.manager.server.RemoteServerManager;
import cafe.jeffrey.local.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.local.core.resources.response.ProfileWithContextResponse;
import cafe.jeffrey.local.core.web.ProjectManagerResolver;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.shared.common.InstantUtils;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Lists all profiles across all workspaces on a single connected jeffrey-server.
 */
@RestController
@RequestMapping("/api/internal/remote-servers/{serverId}/profiles")
public class ProfilesController {

    private static final Logger LOG = LoggerFactory.getLogger(ProfilesController.class);

    private final ProjectManagerResolver resolver;

    public ProfilesController(ProjectManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping
    public List<ProfileWithContextResponse> listAllProfiles(@PathVariable("serverId") String serverId) {
        RemoteServerManager server = resolver.resolveServer(serverId);
        List<ProfileWithContextResponse> allProfiles = new ArrayList<>();

        for (WorkspaceInfo workspaceInfo : server.workspaces()) {
            WorkspaceManager workspaceManager = server.workspace(workspaceInfo.id())
                    .orElse(null);
            if (workspaceManager == null) {
                continue;
            }
            String workspaceName = workspaceInfo.name();
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
        LOG.debug("Listed all profiles on server: server_id={} count={}", serverId, result.size());
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
