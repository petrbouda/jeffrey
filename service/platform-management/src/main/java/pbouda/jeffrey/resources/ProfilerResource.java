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

package pbouda.jeffrey.resources;

import jakarta.ws.rs.*;
import pbouda.jeffrey.common.model.ProfilerInfo;
import pbouda.jeffrey.common.exception.Exceptions;
import pbouda.jeffrey.manager.ProfilerManager;

import java.util.List;
import java.util.Optional;

public class ProfilerResource {

    public record ProfilerSettingsEntity(
            String workspaceId,
            String projectId,
            String agentSettings) {
    }

    private final ProfilerManager profilerManager;

    public ProfilerResource(ProfilerManager profilerManager) {
        this.profilerManager = profilerManager;
    }

    @POST
    @Path("settings")
    public void upsertSettings(ProfilerSettingsEntity request) {
        if (request.workspaceId() == null || request.workspaceId().isBlank()) {
            throw Exceptions.invalidRequest("Workspace ID is required");
        }
        if (request.projectId() == null || request.projectId().isBlank()) {
            throw Exceptions.invalidRequest("Project ID is required");
        }

        ProfilerInfo profilerInfo = new ProfilerInfo(
                request.workspaceId(),
                request.projectId(),
                request.agentSettings());

        profilerManager.upsertSettings(profilerInfo);
    }

    @GET
    @Path("settings/all")
    public List<ProfilerSettingsEntity> findAllSettings() {
        return profilerManager.findAllSettings().stream()
                .map(it -> new ProfilerSettingsEntity(it.workspaceId(), it.projectId(), it.agentSettings()))
                .toList();
    }

    @GET
    @Path("settings")
    public Optional<ProfilerSettingsEntity> findSettings(
            @QueryParam("workspaceId") String workspaceId,
            @QueryParam("projectId") String projectId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw Exceptions.invalidRequest("Workspace ID query parameter is required");
        }
        if (projectId == null || projectId.isBlank()) {
            throw Exceptions.invalidRequest("Project ID query parameter is required");
        }
        return profilerManager.findSettings(workspaceId, projectId)
                .map(it -> new ProfilerSettingsEntity(it.workspaceId(), it.projectId(), it.agentSettings()));
    }

    @DELETE
    @Path("settings")
    public void deleteSettings(
            @QueryParam("workspaceId") String workspaceId,
            @QueryParam("projectId") String projectId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw Exceptions.invalidRequest("Workspace ID query parameter is required");
        }
        if (projectId == null || projectId.isBlank()) {
            throw Exceptions.invalidRequest("Project ID query parameter is required");
        }
        profilerManager.deleteSettings(workspaceId, projectId);
    }
}
