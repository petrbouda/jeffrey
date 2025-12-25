
/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.profile.resources.project.profile;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import pbouda.jeffrey.common.GraphType;
import pbouda.jeffrey.common.config.GraphParameters;
import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.profile.manager.FlamegraphManager;
import pbouda.jeffrey.profile.model.EventSummaryResult;
import pbouda.jeffrey.profile.resources.request.GenerateFlamegraphRequest;

import java.util.List;

import static pbouda.jeffrey.profile.resources.project.profile.FlamegraphResource.mapToGenerateRequest;

/**
 * REST resource for differential flamegraph generation using Protocol Buffers.
 */
public class FlamegraphDiffResource {

    private static final String PROTOBUF_MEDIA_TYPE = "application/x-protobuf";

    private final ProfileInfo profileInfo;
    private final FlamegraphManager diffFlamegraphManager;

    public FlamegraphDiffResource(ProfileInfo profileInfo, FlamegraphManager diffFlamegraphManager) {
        this.profileInfo = profileInfo;
        this.diffFlamegraphManager = diffFlamegraphManager;
    }

    @POST
    @Produces(PROTOBUF_MEDIA_TYPE)
    public byte[] generate(GenerateFlamegraphRequest request) {
        GraphParameters graphParameters = mapToGenerateRequest(profileInfo, request, GraphType.DIFFERENTIAL);
        return diffFlamegraphManager.generate(graphParameters);
    }

    @GET
    @Path("/events")
    public List<EventSummaryResult> events() {
        return diffFlamegraphManager.eventSummaries();
    }
}
