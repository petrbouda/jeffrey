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

package cafe.jeffrey.microscope.core.web.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.microscope.core.manager.remote.RemoteConnection;
import cafe.jeffrey.microscope.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.microscope.core.web.dto.response.ProfileWithContextResponse;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.workspace.WorkspaceInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Lists all profiles across all workspaces on the connected remote.
 */
@RestController
@RequestMapping("/api/internal/remote/profiles")
public class ProfilesController {

    private static final Logger LOG = LoggerFactory.getLogger(ProfilesController.class);

    private final ProjectManagerResolver resolver;

    public ProfilesController(ProjectManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping
    public List<ProfileWithContextResponse> listAllProfiles() {
        RemoteConnection remote = resolver.resolveRemote();
        List<ProfileWithContextResponse> allProfiles = new ArrayList<>();

        for (WorkspaceInfo workspaceInfo : remote.workspaces()) {
            WorkspaceManager workspaceManager = remote.workspace(workspaceInfo.id())
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
                .sorted(Comparator.comparingLong(ProfileWithContextResponse::createdAt).reversed())
                .toList();
        LOG.debug("Listed all profiles on the remote: count={}", result.size());
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
                profileInfo.createdAt().toEpochMilli(),
                profileInfo.eventSource(),
                profileInfo.enabled(),
                profileInfo.modified(),
                profileInfo.duration().toMillis(),
                profileManager.sizeInBytes());
    }
}
