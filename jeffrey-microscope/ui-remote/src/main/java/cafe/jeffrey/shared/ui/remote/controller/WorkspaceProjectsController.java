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

package cafe.jeffrey.shared.ui.remote.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.shared.ui.remote.bridge.RemoteBrowserAccess;
import cafe.jeffrey.shared.ui.remote.dto.ProjectResponse;

import java.util.List;

/**
 * Shared WorkspaceBrowser controller listing a workspace's projects. Both deployments register it via
 * {@code RemoteFeatureConfiguration} and supply a {@link RemoteBrowserAccess} bridge.
 * Profile/namespace listings are microscope-only and live in a deployment-specific supplementary
 * controller.
 */
@RestController
@RequestMapping("/api/internal/remote/workspaces/{workspaceId}/projects")
public class WorkspaceProjectsController {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceProjectsController.class);

    private final RemoteBrowserAccess access;

    public WorkspaceProjectsController(RemoteBrowserAccess access) {
        this.access = access;
    }

    @GetMapping
    public List<ProjectResponse> projects(
            @PathVariable("workspaceId") String workspaceId) {
        List<ProjectResponse> result = access.projects(workspaceId);
        LOG.debug("Listed projects: workspaceId={} count={}", workspaceId, result.size());
        return result;
    }
}
