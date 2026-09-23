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

package cafe.jeffrey.shared.ui.hub.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.shared.ui.hub.bridge.HubBrowserAccess;
import cafe.jeffrey.shared.ui.hub.dto.ProjectResponse;

/**
 * Shared WorkspaceBrowser controller serving a single remote project's info. Both deployments
 * register it via {@code HubsFeatureConfiguration} and supply a {@link HubBrowserAccess}
 * bridge. Write operations (delete/restore) are microscope-only and live in a deployment-specific
 * supplementary controller.
 */
@RestController
@RequestMapping("/api/internal/hubs/{hubId}/workspaces/{workspaceId}/projects/{projectId}")
public class ProjectController {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectController.class);

    private final HubBrowserAccess access;

    public ProjectController(HubBrowserAccess access) {
        this.access = access;
    }

    @GetMapping
    public ProjectResponse info(
            @PathVariable("hubId") String hubId,
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId) {
        LOG.debug("Fetching project info: workspaceId={} projectId={}", workspaceId, projectId);
        return access.project(hubId, workspaceId, projectId);
    }

    @GetMapping("/initializing")
    public boolean initializing() {
        return false;
    }
}
