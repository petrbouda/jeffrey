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
import cafe.jeffrey.performance.analyst.versionsystem.VersionSystemManager;
import cafe.jeffrey.performance.analyst.versionsystem.VersionSystemRequest;
import cafe.jeffrey.performance.analyst.versionsystem.VersionSystemResponse;

/**
 * Reads and writes the version-control integration (GitHub/GitLab) registered for a project. The raw
 * access token is accepted on save but never returned; the GET response only flags whether one is stored.
 */
@RestController
@RequestMapping("/api/internal/hubs/{hubId}/workspaces/{workspaceId}/projects/{projectId}/version-system")
public class VersionSystemController {

    private final VersionSystemManager versionSystemManager;

    public VersionSystemController(VersionSystemManager versionSystemManager) {
        this.versionSystemManager = versionSystemManager;
    }

    @GetMapping
    public VersionSystemResponse get(@PathVariable("projectId") String projectId) {
        return versionSystemManager.find(projectId)
                .map(VersionSystemResponse::of)
                .orElseGet(VersionSystemResponse::empty);
    }

    @PutMapping
    public VersionSystemResponse save(
            @PathVariable("projectId") String projectId,
            @RequestBody VersionSystemRequest request) {
        return VersionSystemResponse.of(
                versionSystemManager.save(projectId, request.platform(), request.url(), request.token()));
    }
}
