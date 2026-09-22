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

import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.microscope.core.manager.ScopedConfigManager;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.microscope.core.web.dto.request.ConfigValueRequest;
import cafe.jeffrey.microscope.core.web.dto.response.ConfigEntryResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The configuration of one project's own scope. */
@RestController
@RequestMapping("/api/internal/hubs/{hubId}/workspaces/{workspaceId}/projects/{projectId}/config")
public class ProjectConfigController {

    private final ProjectManagerResolver resolver;

    public ProjectConfigController(ProjectManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping
    public List<ConfigEntryResponse> fetchConfig(
            @PathVariable("hubId") String hubId,
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId) {

        return ConfigEntryResponse.from(managerFor(hubId, workspaceId, projectId).find());
    }

    @PutMapping("/{type}")
    public List<ConfigEntryResponse> upsertConfig(
            @PathVariable("hubId") String hubId,
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId,
            @PathVariable("type") String type,
            @RequestBody ConfigValueRequest request) {

        if (request == null || request.value() == null || request.value().isBlank()) {
            throw Exceptions.invalidRequest("value is required");
        }

        return ConfigEntryResponse.from(managerFor(hubId, workspaceId, projectId)
                .upsert(WorkspaceConfigController.configType(type), request.value()));
    }

    @DeleteMapping("/{type}")
    public ResponseEntity<Void> deleteConfig(
            @PathVariable("hubId") String hubId,
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId,
            @PathVariable("type") String type) {

        managerFor(hubId, workspaceId, projectId).delete(WorkspaceConfigController.configType(type));
        return ResponseEntity.noContent().build();
    }

    private ScopedConfigManager managerFor(String hubId, String workspaceId, String projectId) {
        return resolver.resolve(hubId, workspaceId, projectId).projectManager().scopedConfigManager();
    }
}
