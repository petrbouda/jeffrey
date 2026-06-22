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

package cafe.jeffrey.performance.analyst.web.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.performance.analyst.versioncontrolsystem.VersionControlSystemManager;
import cafe.jeffrey.performance.analyst.versioncontrolsystem.VersionControlSystemRequest;
import cafe.jeffrey.performance.analyst.versioncontrolsystem.VersionControlSystemResponse;

/**
 * Reads and writes the version-control integration (GitHub/GitLab) registered for a project. The raw
 * access token is accepted on save but never returned; the GET response only flags whether one is stored.
 */
@RestController
@RequestMapping("/api/internal/hubs/{hubId}/workspaces/{workspaceId}/projects/{projectId}/version-control-system")
public class VersionControlSystemController {

    private final VersionControlSystemManager versionControlSystemManager;

    public VersionControlSystemController(VersionControlSystemManager versionControlSystemManager) {
        this.versionControlSystemManager = versionControlSystemManager;
    }

    @GetMapping
    public VersionControlSystemResponse get(@PathVariable("projectId") String projectId) {
        return versionControlSystemManager.find(projectId)
                .map(VersionControlSystemResponse::of)
                .orElseGet(VersionControlSystemResponse::empty);
    }

    @PutMapping
    public VersionControlSystemResponse save(
            @PathVariable("projectId") String projectId,
            @RequestBody VersionControlSystemRequest request) {
        return VersionControlSystemResponse.of(
                versionControlSystemManager.save(projectId, request.platform(), request.url(), request.token()));
    }
}
