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

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import pbouda.jeffrey.common.IDGenerator;
import pbouda.jeffrey.common.model.ProfilerInfo;
import pbouda.jeffrey.manager.ProfilerManager;

public class ProfilerResource {

    public record ProfilerSettingsRequest(
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
    public void upsertSettings(ProfilerSettingsRequest request) {
        ProfilerInfo profilerInfo = new ProfilerInfo(
                IDGenerator.generate(),
                request.workspaceId,
                request.projectId,
                request.agentSettings);

        profilerManager.upsertSettings(profilerInfo);
    }
}
