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

package pbouda.jeffrey.platform.resources;

import jakarta.ws.rs.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.platform.resources.request.ProfilerSettingsRequest;
import pbouda.jeffrey.provider.platform.repository.ProfilerRepository;
import pbouda.jeffrey.shared.common.exception.Exceptions;
import pbouda.jeffrey.shared.common.model.ProfilerInfo;

import java.util.List;

public class ProfilerResource {

    private static final Logger LOG = LoggerFactory.getLogger(ProfilerResource.class);

    private final ProfilerRepository profilerRepository;

    public ProfilerResource(ProfilerRepository profilerRepository) {
        this.profilerRepository = profilerRepository;
    }

    @POST
    @Path("settings")
    public void upsertSettings(ProfilerSettingsRequest request) {
        LOG.debug("Upserting profiler settings: workspaceId={} projectId={}", request.workspaceId(), request.projectId());
        // Validate hierarchy: if projectId is provided, workspaceId must also be provided
        // Valid combinations:
        // - GLOBAL:    workspaceId=null, projectId=null
        // - WORKSPACE: workspaceId=<id>, projectId=null
        // - PROJECT:   workspaceId=<id>, projectId=<id>
        String workspaceId = normalizeToNull(request.workspaceId());
        String projectId = normalizeToNull(request.projectId());

        if (projectId != null && workspaceId == null) {
            throw Exceptions.invalidRequest("Workspace ID is required when Project ID is provided");
        }

        ProfilerInfo profilerInfo = new ProfilerInfo(workspaceId, projectId, request.agentSettings());
        profilerRepository.upsertSettings(profilerInfo);
    }

    private static String normalizeToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    @GET
    @Path("settings")
    public List<ProfilerSettingsRequest> findAllSettings() {
        LOG.debug("Listing profiler settings");
        return profilerRepository.findAllSettings().stream()
                .map(it -> new ProfilerSettingsRequest(it.workspaceId(), it.projectId(), it.agentSettings()))
                .toList();
    }

    @DELETE
    @Path("settings")
    public void deleteSettings(
            @QueryParam("workspaceId") String workspaceId,
            @QueryParam("projectId") String projectId) {
        LOG.debug("Deleting profiler settings: workspaceId={} projectId={}", workspaceId, projectId);
        // Normalize and validate hierarchy
        String wsId = normalizeToNull(workspaceId);
        String projId = normalizeToNull(projectId);

        if (projId != null && wsId == null) {
            throw Exceptions.invalidRequest("Workspace ID is required when Project ID is provided");
        }

        profilerRepository.deleteSettings(wsId, projId);
    }
}
