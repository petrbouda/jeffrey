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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.local.core.client.RemoteProfilerClient;
import cafe.jeffrey.local.core.manager.server.RemoteServerManager;
import cafe.jeffrey.local.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.local.core.resources.request.ProfilerSettingsRequest;
import cafe.jeffrey.local.core.web.ProjectManagerResolver;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.model.ProfilerInfo;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/internal/remote-servers/{serverId}/profiler")
public class ProfilerController {

    private static final Logger LOG = LoggerFactory.getLogger(ProfilerController.class);

    private final ProjectManagerResolver resolver;

    public ProfilerController(ProjectManagerResolver resolver) {
        this.resolver = resolver;
    }

    @PostMapping("/settings")
    public void upsertSettings(
            @PathVariable("serverId") String serverId,
            @RequestBody ProfilerSettingsRequest request) {

        String workspaceId = normalizeToNull(request.workspaceId());
        String projectId = normalizeToNull(request.projectId());

        LOG.debug("Upserting profiler settings: server_id={} workspaceId={} projectId={}",
                serverId, workspaceId, projectId);

        if (projectId != null && workspaceId == null) {
            throw Exceptions.invalidRequest("Workspace ID is required when Project ID is provided");
        }

        if (workspaceId == null) {
            throw Exceptions.invalidRequest("Workspace ID is required to identify the target server");
        }

        RemoteProfilerClient client = findProfilerClient(serverId, workspaceId);
        client.upsertSettingsAtLevel(workspaceId, projectId, request.agentSettings());
    }

    @GetMapping("/settings")
    public List<ProfilerSettingsRequest> findAllSettings(@PathVariable("serverId") String serverId) {
        RemoteServerManager server = resolver.resolveServer(serverId);
        List<ProfilerSettingsRequest> result = new ArrayList<>();

        for (WorkspaceInfo wsInfo : server.workspaces()) {
            WorkspaceManager workspace = server.workspace(wsInfo.id()).orElse(null);
            if (workspace == null) {
                continue;
            }
            workspace.profilerClient().ifPresent(client -> {
                try {
                    List<ProfilerInfo> settings = client.listAllSettings();
                    for (ProfilerInfo info : settings) {
                        result.add(new ProfilerSettingsRequest(
                                info.workspaceId(), info.projectId(), info.agentSettings()));
                    }
                } catch (Exception e) {
                    LOG.warn("Failed to fetch profiler settings: workspaceId={}", wsInfo.id(), e);
                }
            });
        }

        LOG.debug("Listed profiler settings: server_id={} count={}", serverId, result.size());
        return result;
    }

    @DeleteMapping("/settings")
    public void deleteSettings(
            @PathVariable("serverId") String serverId,
            @RequestParam(value = "workspaceId", required = false) String workspaceId,
            @RequestParam(value = "projectId", required = false) String projectId) {

        String wsId = normalizeToNull(workspaceId);
        String projId = normalizeToNull(projectId);

        LOG.debug("Deleting profiler settings: server_id={} workspaceId={} projectId={}",
                serverId, wsId, projId);

        if (projId != null && wsId == null) {
            throw Exceptions.invalidRequest("Workspace ID is required when Project ID is provided");
        }

        if (wsId == null) {
            throw Exceptions.invalidRequest("Workspace ID is required to identify the target server");
        }

        RemoteProfilerClient client = findProfilerClient(serverId, wsId);
        client.deleteSettingsAtLevel(wsId, projId);
    }

    private RemoteProfilerClient findProfilerClient(String serverId, String workspaceId) {
        WorkspaceManager workspace = resolver.resolveWorkspace(serverId, workspaceId);
        return workspace.profilerClient()
                .orElseThrow(() -> Exceptions.invalidRequest(
                        "Workspace does not support remote profiler settings: " + workspaceId));
    }

    private static String normalizeToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
