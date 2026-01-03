/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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
import pbouda.jeffrey.shared.common.model.ProfilerInfo;
import pbouda.jeffrey.shared.common.exception.Exceptions;
import pbouda.jeffrey.provider.platform.repository.ProfilerRepository;

import java.util.List;

public class ProfilerResource {

    public record ProfilerSettingsEntity(
            String workspaceId,
            String projectId,
            String agentSettings) {
    }

    private final ProfilerRepository profilerRepository;

    public ProfilerResource(ProfilerRepository profilerRepository) {
        this.profilerRepository = profilerRepository;
    }

    @POST
    @Path("settings")
    public void upsertSettings(ProfilerSettingsEntity request) {
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
    public List<ProfilerSettingsEntity> findAllSettings() {
        return profilerRepository.findAllSettings().stream()
                .map(it -> new ProfilerSettingsEntity(it.workspaceId(), it.projectId(), it.agentSettings()))
                .toList();
    }

    @DELETE
    @Path("settings")
    public void deleteSettings(
            @QueryParam("workspaceId") String workspaceId,
            @QueryParam("projectId") String projectId) {
        // Normalize and validate hierarchy
        String wsId = normalizeToNull(workspaceId);
        String projId = normalizeToNull(projectId);

        if (projId != null && wsId == null) {
            throw Exceptions.invalidRequest("Workspace ID is required when Project ID is provided");
        }

        profilerRepository.deleteSettings(wsId, projId);
    }
}
