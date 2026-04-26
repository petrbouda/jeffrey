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

package pbouda.jeffrey.local.core.web.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pbouda.jeffrey.local.core.client.RemoteProfilerClient;
import pbouda.jeffrey.local.core.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.local.core.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.local.core.resources.request.ProfilerSettingsRequest;
import pbouda.jeffrey.shared.common.exception.Exceptions;
import pbouda.jeffrey.shared.common.model.ProfilerInfo;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/internal/profiler")
public class ProfilerController {

    private static final Logger LOG = LoggerFactory.getLogger(ProfilerController.class);

    private final WorkspacesManager workspacesManager;

    public ProfilerController(WorkspacesManager workspacesManager) {
        this.workspacesManager = workspacesManager;
    }

    @PostMapping("/settings")
    public void upsertSettings(@RequestBody ProfilerSettingsRequest request) {
        String workspaceId = normalizeToNull(request.workspaceId());
        String projectId = normalizeToNull(request.projectId());

        LOG.debug("Upserting profiler settings: workspaceId={} projectId={}", workspaceId, projectId);

        if (projectId != null && workspaceId == null) {
            throw Exceptions.invalidRequest("Workspace ID is required when Project ID is provided");
        }

        if (workspaceId == null) {
            throw Exceptions.invalidRequest("Workspace ID is required to identify the target server");
        }

        RemoteProfilerClient client = findProfilerClient(workspaceId);
        client.upsertSettingsAtLevel(workspaceId, projectId, request.agentSettings());
    }

    @GetMapping("/settings")
    public List<ProfilerSettingsRequest> findAllSettings() {
        List<ProfilerSettingsRequest> result = new ArrayList<>();

        for (WorkspaceManager workspace : workspacesManager.findAll()) {
            workspace.profilerClient().ifPresent(client -> {
                try {
                    List<ProfilerInfo> settings = client.listAllSettings();
                    for (ProfilerInfo info : settings) {
                        result.add(new ProfilerSettingsRequest(
                                info.workspaceId(), info.projectId(), info.agentSettings()));
                    }
                } catch (Exception e) {
                    LOG.warn("Failed to fetch profiler settings from workspace: workspaceId={}",
                            workspace.localInfo().id(), e);
                }
            });
        }

        LOG.debug("Listed profiler settings: count={}", result.size());
        return result;
    }

    @DeleteMapping("/settings")
    public void deleteSettings(
            @RequestParam(value = "workspaceId", required = false) String workspaceId,
            @RequestParam(value = "projectId", required = false) String projectId) {

        String wsId = normalizeToNull(workspaceId);
        String projId = normalizeToNull(projectId);

        LOG.debug("Deleting profiler settings: workspaceId={} projectId={}", wsId, projId);

        if (projId != null && wsId == null) {
            throw Exceptions.invalidRequest("Workspace ID is required when Project ID is provided");
        }

        if (wsId == null) {
            throw Exceptions.invalidRequest("Workspace ID is required to identify the target server");
        }

        RemoteProfilerClient client = findProfilerClient(wsId);
        client.deleteSettingsAtLevel(wsId, projId);
    }

    private RemoteProfilerClient findProfilerClient(String workspaceId) {
        WorkspaceManager workspace = workspacesManager.findById(workspaceId)
                .orElseThrow(() -> Exceptions.invalidRequest("Workspace not found: " + workspaceId));

        return workspace.profilerClient()
                .orElseThrow(() -> Exceptions.invalidRequest(
                        "Workspace does not support remote profiler settings: " + workspaceId));
    }

    private static String normalizeToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
