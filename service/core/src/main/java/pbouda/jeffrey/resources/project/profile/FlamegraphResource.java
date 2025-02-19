
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

import jakarta.ws.rs.*;
import pbouda.jeffrey.common.config.GraphParameters;
import pbouda.jeffrey.common.model.profile.ProfileInfo;
import pbouda.jeffrey.flamegraph.api.GraphData;
import pbouda.jeffrey.manager.FlamegraphManager;
import pbouda.jeffrey.model.EventSummaryResult;
import pbouda.jeffrey.repository.model.GraphContent;
import pbouda.jeffrey.repository.model.GraphInfo;
import pbouda.jeffrey.resources.request.GenerateFlamegraphRequest;
import pbouda.jeffrey.timeseries.TimeseriesUtils;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public class FlamegraphResource {

    private final ProfileInfo profileInfo;
    private final FlamegraphManager flamegraphManager;

    public FlamegraphResource(ProfileInfo profileInfo, FlamegraphManager flamegraphManager) {
        this.profileInfo = profileInfo;
        this.flamegraphManager = flamegraphManager;
    }

    @POST
    public GraphData generate(GenerateFlamegraphRequest request) {
        Instant recordingStart = profileInfo.startedAt();

        GraphData data = flamegraphManager.generate(mapToGenerateRequest(request));
        /*
         * Current Timeseries graph counts on the fact that the timeseries data is available in absolute values
         * (not relative ones based on the recording start). We need to move the points to the absolute time.
         */
        if (data.timeseries() != null) {
            TimeseriesUtils.toAbsoluteTime(data.timeseries(), recordingStart.toEpochMilli());
        }

        return data;
    }

    @POST
    @Path("/save")
    public void saveRange(GenerateFlamegraphRequest request) {
        flamegraphManager.save(mapToGenerateRequest(request), request.flamegraphName());
    }

    @GET
    public List<GraphInfo> list() {
        return flamegraphManager.allCustom().stream()
                .sorted(Comparator.comparing(GraphInfo::createdAt).reversed())
                .toList();
    }

    @GET
    @Path("/events")
    public List<EventSummaryResult> events() {
        return flamegraphManager.eventSummaries();
    }

    @GET
    @Path("/{flamegraphId}")
    public GraphContent getContentById(@PathParam("flamegraphId") String flamegraphId) {
        return flamegraphManager.get(flamegraphId)
                .orElseThrow(() -> new NotFoundException("Flamegraph not found"));
    }

    @DELETE
    @Path("/{flamegraphId}")
    public void delete(@PathParam("flamegraphId") String flamegraphId) {
        flamegraphManager.delete(flamegraphId);
    }

//    @POST
//    @Path("/{flamegraphId}/export")
//    public void exportBytId(@PathParam("flamegraphId") String flamegraphId) {
//        flamegraphManager.export(flamegraphId);
//    }

//    @POST
//    @Path("/export")
//    public void export(GenerateFlamegraphRequest request) {
//        flamegraphManager.export(mapToGenerateRequest(request));
//    }

    static FlamegraphManager.Generate mapToGenerateRequest(GenerateFlamegraphRequest request) {
        GraphParameters graphParameters = GraphParameters.builder()
                .withThreadMode(request.useThreadMode())
                .withCollectWeight(request.useWeight())
                .withExcludeNonJavaSamples(request.excludeNonJavaSamples())
                .withExcludeIdleSamples(request.excludeIdleSamples())
                .withOnlyUnsafeAllocationSamples(request.onlyUnsafeAllocationSamples())
                .withParseLocation(true)
                .withGraphComponents(request.components())
                .build();

        return new FlamegraphManager.Generate(
                request.eventType(),
                request.timeRange(),
                graphParameters,
                request.threadInfo(),
                request.markers());
    }
}
