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

package pbouda.jeffrey.platform.resources.project;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.core.Response;
import pbouda.jeffrey.platform.manager.ProfilerSettingsManager;
import pbouda.jeffrey.platform.resources.request.ProfilerSettingsRequest;
import pbouda.jeffrey.platform.resources.response.ProfilerSettingsResponse;

/**
 * Internal REST endpoint for profiler settings at the project level.
 * Works with both LIVE and REMOTE workspaces - the ProjectManager handles the delegation.
 */
public class ProjectProfilerSettingsResource {

    private final ProfilerSettingsManager profilerSettingsManager;

    public ProjectProfilerSettingsResource(ProfilerSettingsManager profilerSettingsManager) {
        this.profilerSettingsManager = profilerSettingsManager;
    }

    /**
     * Fetches the effective profiler settings for this project,
     * resolved from the hierarchy: project > workspace > global.
     */
    @GET
    public ProfilerSettingsResponse fetchSettings() {
        return ProfilerSettingsResponse.from(profilerSettingsManager.fetchEffectiveSettings());
    }

    /**
     * Upserts profiler settings for this project.
     */
    @POST
    public Response upsertSettings(ProfilerSettingsRequest request) {
        profilerSettingsManager.upsertSettings(request.agentSettings());
        return Response.ok().build();
    }

    /**
     * Deletes project-level profiler settings.
     */
    @DELETE
    public Response deleteSettings() {
        profilerSettingsManager.deleteSettings();
        return Response.noContent().build();
    }
}
