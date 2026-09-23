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

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.microscope.core.web.dto.request.ProjectSettingsUpdateRequest;
import cafe.jeffrey.microscope.core.web.dto.response.ProjectSettingsResponse;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;

@RestController
@RequestMapping("/api/internal/hubs/{hubId}/workspaces/{workspaceId}/projects/{projectId}/settings")
public class ProjectSettingsController {

    private final ProjectManagerResolver resolver;

    public ProjectSettingsController(ProjectManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping
    public ProjectSettingsResponse settings(
            @PathVariable("hubId") String hubId,
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId) {
        ProjectManager pm = resolver.resolve(hubId, workspaceId, projectId).projectManager();
        return ProjectSettingsResponse.create(pm.info());
    }

    @PostMapping
    public void update(
            @PathVariable("hubId") String hubId,
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId,
            @RequestBody ProjectSettingsUpdateRequest request) {
        ProjectManager pm = resolver.resolve(hubId, workspaceId, projectId).projectManager();
        if (request.name() != null) {
            pm.updateName(request.name());
        }
    }
}
