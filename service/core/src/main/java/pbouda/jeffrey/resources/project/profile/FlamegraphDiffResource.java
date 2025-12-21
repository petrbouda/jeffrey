
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

package pbouda.jeffrey.resources.project.profile;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import pbouda.jeffrey.common.GraphType;
import pbouda.jeffrey.common.MsgPack;
import pbouda.jeffrey.common.config.GraphParameters;
import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.flamegraph.api.GraphData;
import pbouda.jeffrey.manager.FlamegraphManager;
import pbouda.jeffrey.model.EventSummaryResult;
import pbouda.jeffrey.resources.request.GenerateFlamegraphRequest;
import pbouda.jeffrey.timeseries.TimeseriesUtils;

import java.time.Instant;
import java.util.List;

import static pbouda.jeffrey.resources.project.profile.FlamegraphResource.mapToGenerateRequest;

public class FlamegraphDiffResource {

    private final ProfileInfo profileInfo;
    private final FlamegraphManager diffFlamegraphManager;

    public FlamegraphDiffResource(ProfileInfo profileInfo, FlamegraphManager diffFlamegraphManager) {
        this.profileInfo = profileInfo;
        this.diffFlamegraphManager = diffFlamegraphManager;
    }

    @POST
    @Produces({MediaType.APPLICATION_JSON, MsgPack.MEDIA_TYPE})
    public Response generate(GenerateFlamegraphRequest request, @Context HttpHeaders headers) {
        Instant recordingStart = profileInfo.profilingStartedAt();

        GraphParameters graphParameters = mapToGenerateRequest(profileInfo, request, GraphType.DIFFERENTIAL);
        GraphData data = diffFlamegraphManager.generate(graphParameters);
        /*
         * Current Timeseries graph counts on the fact that the timeseries data is available in absolute values
         * (not relative ones based on the recording start). We need to move the points to the absolute time.
         */
        if (data.timeseries() != null) {
            TimeseriesUtils.toAbsoluteTime(data.timeseries(), recordingStart.toEpochMilli());
        }

        // Content negotiation: return MessagePack if requested, otherwise JSON
        String acceptHeader = headers.getHeaderString(HttpHeaders.ACCEPT);
        if (acceptHeader != null && acceptHeader.contains(MsgPack.MEDIA_TYPE)) {
            return Response.ok(MsgPack.toBytes(data))
                    .type(MsgPack.MEDIA_TYPE)
                    .build();
        }

        return Response.ok(data)
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    @GET
    @Path("/events")
    public List<EventSummaryResult> events() {
        return diffFlamegraphManager.eventSummaries();
    }
}
