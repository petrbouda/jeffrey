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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.local.core.manager.project.ProjectManager;
import cafe.jeffrey.local.core.resources.response.ProjectResponse;
import cafe.jeffrey.local.core.resources.workspace.Mappers;
import cafe.jeffrey.local.core.web.ProjectManagerResolver;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventCreator;

@RestController
@RequestMapping("/api/internal/workspaces/{workspaceId}/projects/{projectId}")
public class ProjectController {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectController.class);

    private final ProjectManagerResolver resolver;

    public ProjectController(ProjectManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping
    public ProjectResponse info(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId) {
        ProjectManager pm = resolver.resolve(workspaceId, projectId).projectManager();
        LOG.debug("Fetching project info: projectId={}", pm.info().id());
        return Mappers.toProjectResponse(pm.detailedInfo());
    }

    @DeleteMapping
    public void delete(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId) {
        ProjectManager pm = resolver.resolve(workspaceId, projectId).projectManager();
        LOG.debug("Deleting project: projectId={}", pm.info().id());
        pm.delete(WorkspaceEventCreator.MANUAL);
    }

    @GetMapping("/initializing")
    public boolean initializing() {
        return false;
    }

    @PostMapping("/restore")
    public void restore(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId) {
        ProjectManager pm = resolver.resolve(workspaceId, projectId).projectManager();
        LOG.debug("Restoring project: projectId={}", pm.info().id());
        pm.restore();
    }
}
