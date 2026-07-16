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
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.ProjectInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Microscope-only WorkspaceBrowser endpoints: projects with their profile summaries, and the unique
 * namespaces across a workspace's projects. Both depend on profiles, which the analyst lacks, so they
 * live outside the shared {@code WorkspaceProjectsController}.
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
                                profileInfo.createdAt());
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
