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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;

/**
 * Microscope-only WorkspaceBrowser endpoints: deleting and restoring a project. Deployments that are
 * read-only against remote hubs do not have them, so these live outside the shared
 * {@code ProjectController}.
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
        pm.delete();
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
