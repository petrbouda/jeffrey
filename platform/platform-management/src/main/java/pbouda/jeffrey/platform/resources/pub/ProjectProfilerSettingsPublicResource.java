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

package pbouda.jeffrey.platform.resources.pub;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.core.Response;
import pbouda.jeffrey.platform.EffectiveSettingsResolver;
import pbouda.jeffrey.platform.resources.request.ProfilerSettingsRequest;
import pbouda.jeffrey.platform.resources.response.ProfilerSettingsResponse;
import pbouda.jeffrey.provider.platform.repository.ProfilerRepository;
import pbouda.jeffrey.shared.common.model.ProfilerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Public REST endpoint for profiler settings at the project level.
 * This enables remote workspaces to fetch and manage profiler settings.
 */
public class ProjectProfilerSettingsPublicResource {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectProfilerSettingsPublicResource.class);

    private final ProfilerRepository profilerRepository;
    private final String workspaceId;
    private final String projectId;

    public ProjectProfilerSettingsPublicResource(
            ProfilerRepository profilerRepository,
            String workspaceId,
            String projectId) {

        this.profilerRepository = profilerRepository;
        this.workspaceId = workspaceId;
        this.projectId = projectId;
    }

    /**
     * Fetches the effective profiler settings for this project,
     * resolved from the hierarchy: project > workspace > global.
     */
    @GET
    public ProfilerSettingsResponse fetchSettings() {
        LOG.debug("Fetching public profiler settings");
        List<ProfilerInfo> allSettings = profilerRepository.fetchProfilerSettings(workspaceId, projectId);
        return ProfilerSettingsResponse.from(EffectiveSettingsResolver.resolve(allSettings));
    }

    /**
     * Upserts profiler settings for this project.
     * Only project-level settings are allowed via this endpoint.
     */
    @POST
    public Response upsertSettings(ProfilerSettingsRequest request) {
        LOG.debug("Upserting public profiler settings");
        ProfilerInfo profilerInfo = new ProfilerInfo(workspaceId, projectId, request.agentSettings());
        profilerRepository.upsertSettings(profilerInfo);
        return Response.ok().build();
    }

    /**
     * Deletes project-level profiler settings.
     */
    @DELETE
    public Response deleteSettings() {
        LOG.debug("Deleting public profiler settings");
        profilerRepository.deleteSettings(workspaceId, projectId);
        return Response.noContent().build();
    }
}
