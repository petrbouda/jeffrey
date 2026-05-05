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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.microscope.core.resources.request.ProfilerSettingsRequest;
import cafe.jeffrey.microscope.core.resources.response.CurrentProfilerSettingsResponse;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.shared.common.exception.Exceptions;

@RestController
@RequestMapping("/api/internal/remote-servers/{serverId}/workspaces/{workspaceId}/profiler/settings")
public class WorkspaceProfilerSettingsController {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceProfilerSettingsController.class);

    private final ProjectManagerResolver resolver;

    public WorkspaceProfilerSettingsController(ProjectManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping
    public CurrentProfilerSettingsResponse fetchCurrent(
            @PathVariable("serverId") String serverId,
            @PathVariable("workspaceId") String workspaceId) {

        WorkspaceManager workspace = resolver.resolveWorkspace(serverId, workspaceId);
        var levels = workspace.fetchEffectiveProfilerSettings();
        LOG.debug("Fetched workspace profiler settings: serverId={} workspaceId={} workspaceSet={} globalSet={}",
                serverId, workspaceId,
                levels.workspaceSettings() != null,
                levels.globalSettings() != null);
        return CurrentProfilerSettingsResponse.from(levels);
    }

    @PostMapping
    public void upsert(
            @PathVariable("serverId") String serverId,
            @PathVariable("workspaceId") String workspaceId,
            @RequestBody ProfilerSettingsRequest request) {

        if (request == null || request.agentSettings() == null || request.agentSettings().isBlank()) {
            throw Exceptions.invalidRequest("agentSettings is required");
        }

        WorkspaceManager workspace = resolver.resolveWorkspace(serverId, workspaceId);
        workspace.upsertProfilerSettings(request.agentSettings());
        LOG.debug("Upserted workspace profiler settings: serverId={} workspaceId={}", serverId, workspaceId);
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(
            @PathVariable("serverId") String serverId,
            @PathVariable("workspaceId") String workspaceId) {

        WorkspaceManager workspace = resolver.resolveWorkspace(serverId, workspaceId);
        workspace.deleteProfilerSettings();
        LOG.debug("Removed workspace profiler settings override: serverId={} workspaceId={}", serverId, workspaceId);
        return ResponseEntity.noContent().build();
    }
}
