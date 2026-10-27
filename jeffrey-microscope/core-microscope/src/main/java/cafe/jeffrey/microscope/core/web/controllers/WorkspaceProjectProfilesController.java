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
import cafe.jeffrey.microscope.core.manager.project.ProjectsManager;
import cafe.jeffrey.microscope.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.microscope.core.web.dto.response.ProfileInfoResponse;
import cafe.jeffrey.microscope.core.web.dto.response.ProjectWithProfilesResponse;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.ProjectInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Microscope-only WorkspaceBrowser endpoints: projects with their profile summaries, and the unique
 * namespaces across a workspace's projects. Both depend on profiles, which a profile-less deployment
 * lacks, so they live outside the shared {@code WorkspaceProjectsController}.
 */
@RestController
@RequestMapping("/api/internal/hubs/{hubId}/workspaces/{workspaceId}/projects")
public class WorkspaceProjectProfilesController {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceProjectProfilesController.class);

    private final ProjectManagerResolver resolver;

    public WorkspaceProjectProfilesController(ProjectManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping("/profiles")
    public List<ProjectWithProfilesResponse> projectsWithProfiles(
            @PathVariable("hubId") String hubId,
            @PathVariable("workspaceId") String workspaceId) {
        WorkspaceManager workspace = resolver.resolveWorkspace(hubId, workspaceId);
        ProjectsManager projectsManager = workspace.projectsManager();
        List<ProjectWithProfilesResponse> responses = new ArrayList<>();
        for (ProjectManager projectManager : projectsManager.findAll()) {
            ProjectInfo projectInfo = projectManager.info();

            List<ProfileInfoResponse> profiles = projectManager.profilesManager().allProfiles().stream()
                    .map(manager -> {
                        ProfileInfo profileInfo = manager.info();
                        return new ProfileInfoResponse(
                                profileInfo.id(),
                                profileInfo.name(),
                                projectInfo.id(),
                                profileInfo.createdAt().toEpochMilli());
                    })
                    .toList();

            responses.add(new ProjectWithProfilesResponse(projectInfo.id(), projectInfo.name(), profiles));
        }
        LOG.debug("Listed projects with profiles: workspaceId={} projectCount={}", workspaceId, responses.size());
        return responses;
    }

    @GetMapping("/namespaces")
    public List<String> namespaces(@PathVariable("hubId") String hubId, @PathVariable("workspaceId") String workspaceId) {
        WorkspaceManager workspace = resolver.resolveWorkspace(hubId, workspaceId);
        var result = workspace.projectsManager().findAllNamespaces();
        LOG.debug("Listed namespaces: workspaceId={} count={}", workspaceId, result.size());
        return result;
    }
}
