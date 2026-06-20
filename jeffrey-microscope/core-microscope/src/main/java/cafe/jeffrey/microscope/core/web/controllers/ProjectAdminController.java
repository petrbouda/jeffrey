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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventCreator;

/**
 * Microscope-only WorkspaceBrowser endpoints: deleting and restoring a project. The analyst is
 * read-only against remote hubs, so these live outside the shared {@code ProjectController}.
 */
@RestController
@RequestMapping("/api/internal/hubs/{hubId}/workspaces/{workspaceId}/projects/{projectId}")
public class ProjectAdminController {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectAdminController.class);

    private final ProjectManagerResolver resolver;

    public ProjectAdminController(ProjectManagerResolver resolver) {
        this.resolver = resolver;
    }

    @DeleteMapping
    public void delete(
            @PathVariable("hubId") String hubId,
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId) {
        ProjectManager pm = resolver.resolve(hubId, workspaceId, projectId).projectManager();
        LOG.debug("Deleting project: projectId={}", pm.info().id());
        pm.delete(WorkspaceEventCreator.MANUAL);
    }

    @PostMapping("/restore")
    public void restore(
            @PathVariable("hubId") String hubId,
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId) {
        ProjectManager pm = resolver.resolve(hubId, workspaceId, projectId).projectManager();
        LOG.debug("Restoring project: projectId={}", pm.info().id());
        pm.restore();
    }
}
