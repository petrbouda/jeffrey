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

package pbouda.jeffrey.local.core.resources;

import jakarta.ws.rs.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.local.core.client.RemoteProfilerClient;
import pbouda.jeffrey.local.core.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.local.core.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.local.core.resources.request.ProfilerSettingsRequest;
import pbouda.jeffrey.shared.common.exception.Exceptions;
import pbouda.jeffrey.shared.common.model.ProfilerInfo;

import java.util.ArrayList;
import java.util.List;

public class ProfilerResource {

    private static final Logger LOG = LoggerFactory.getLogger(ProfilerResource.class);

    private final WorkspacesManager workspacesManager;

    public ProfilerResource(WorkspacesManager workspacesManager) {
        this.workspacesManager = workspacesManager;
    }

    @POST
    @Path("settings")
    public void upsertSettings(ProfilerSettingsRequest request) {
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

    @GET
    @Path("settings")
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

    @DELETE
    @Path("settings")
    public void deleteSettings(
            @QueryParam("workspaceId") String workspaceId,
            @QueryParam("projectId") String projectId) {

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
