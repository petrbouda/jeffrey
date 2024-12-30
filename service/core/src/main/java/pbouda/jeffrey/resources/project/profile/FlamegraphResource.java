
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

import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ws.rs.*;
import pbouda.jeffrey.common.config.GraphParameters;
import pbouda.jeffrey.manager.FlamegraphManager;
import pbouda.jeffrey.model.EventSummaryResult;
import pbouda.jeffrey.repository.model.GraphContent;
import pbouda.jeffrey.repository.model.GraphInfo;
import pbouda.jeffrey.resources.request.GenerateFlamegraphRequest;

import java.util.Comparator;
import java.util.List;

public class FlamegraphResource {

    private final FlamegraphManager flamegraphManager;

    public FlamegraphResource(FlamegraphManager flamegraphManager) {
        this.flamegraphManager = flamegraphManager;
    }

    @POST
    public ObjectNode generate(GenerateFlamegraphRequest request) {
        return flamegraphManager.generate(mapToGenerateRequest(request));
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
                .build();

        return new FlamegraphManager.Generate(
                request.eventType(),
                request.timeRange(),
                graphParameters,
                request.threadInfo(),
                request.markers());
    }
}
