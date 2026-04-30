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

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.local.core.manager.project.ProjectManager;
import cafe.jeffrey.local.core.resources.request.ProjectSettingsUpdateRequest;
import cafe.jeffrey.local.core.resources.response.ProjectSettingsResponse;
import cafe.jeffrey.local.core.web.ProjectManagerResolver;

@RestController
@RequestMapping("/api/internal/remote-servers/{serverId}/workspaces/{workspaceId}/projects/{projectId}/settings")
public class ProjectSettingsController {

    private final ProjectManagerResolver resolver;

    public ProjectSettingsController(ProjectManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping
    public ProjectSettingsResponse settings(
            @PathVariable("serverId") String serverId,
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId) {
        ProjectManager pm = resolver.resolve(serverId, workspaceId, projectId).projectManager();
        return ProjectSettingsResponse.create(pm.info());
    }

    @PostMapping
    public void update(
            @PathVariable("serverId") String serverId,
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId,
            @RequestBody ProjectSettingsUpdateRequest request) {
        ProjectManager pm = resolver.resolve(serverId, workspaceId, projectId).projectManager();
        if (request.name() != null) {
            pm.updateName(request.name());
        }
    }
}
