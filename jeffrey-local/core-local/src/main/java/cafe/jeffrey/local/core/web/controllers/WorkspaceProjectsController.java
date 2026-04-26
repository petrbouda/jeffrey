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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.local.core.manager.project.ProjectManager;
import cafe.jeffrey.local.core.manager.project.ProjectsManager;
import cafe.jeffrey.local.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.local.core.resources.response.ProfileInfoResponse;
import cafe.jeffrey.local.core.resources.response.ProjectResponse;
import cafe.jeffrey.local.core.resources.response.ProjectWithProfilesResponse;
import cafe.jeffrey.local.core.resources.workspace.Mappers;
import cafe.jeffrey.local.core.web.ProjectManagerResolver;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.ProjectInfo;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/internal/workspaces/{workspaceId}/projects")
public class WorkspaceProjectsController {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceProjectsController.class);

    private final ProjectManagerResolver resolver;

    public WorkspaceProjectsController(ProjectManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping
    public List<ProjectResponse> projects(
            @PathVariable("workspaceId") String workspaceId,
            @RequestParam(value = "includeDeleted", defaultValue = "false") boolean includeDeleted) {
        WorkspaceManager workspace = resolver.resolveWorkspace(workspaceId);
        ProjectsManager projectsManager = workspace.projectsManager();
        var managers = includeDeleted
                ? projectsManager.findAllIncludingDeleted()
                : projectsManager.findAll();
        var result = managers.stream()
                .map(ProjectManager::detailedInfo)
                .map(Mappers::toProjectResponse)
                .toList();
        LOG.debug("Listed projects: workspaceId={} includeDeleted={} count={}", workspaceId, includeDeleted, result.size());
        return result;
    }

    @GetMapping("/profiles")
    public List<ProjectWithProfilesResponse> projectsWithProfiles(
            @PathVariable("workspaceId") String workspaceId) {
        WorkspaceManager workspace = resolver.resolveWorkspace(workspaceId);
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
    public List<String> namespaces(@PathVariable("workspaceId") String workspaceId) {
        WorkspaceManager workspace = resolver.resolveWorkspace(workspaceId);
        var result = workspace.projectsManager().findAllNamespaces();
        LOG.debug("Listed namespaces: workspaceId={} count={}", workspaceId, result.size());
        return result;
    }
}
