
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
import pbouda.jeffrey.manager.FlamegraphManager;
import pbouda.jeffrey.model.EventSummaryResult;
import pbouda.jeffrey.repository.model.GraphContent;
import pbouda.jeffrey.repository.model.GraphInfo;
import pbouda.jeffrey.resources.request.ExportRequest;
import pbouda.jeffrey.resources.request.GenerateFlamegraphRequest;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class FlamegraphResource {

    private final FlamegraphManager flamegraphManager;

    public FlamegraphResource(FlamegraphManager flamegraphManager) {
        this.flamegraphManager = flamegraphManager;
    }

    @POST
    public ObjectNode generate(GenerateFlamegraphRequest request) {
        FlamegraphManager.Generate generateRequest = new FlamegraphManager.Generate(
                request.eventType(),
                request.timeRange(),
                request.useThreadMode(),
                request.excludeNonJavaSamples(),
                request.excludeIdleSamples());

        return flamegraphManager.generate(generateRequest);
    }

    @POST
    @Path("/save")
    public void saveRange(GenerateFlamegraphRequest request) {
        FlamegraphManager.Generate generateRequest = new FlamegraphManager.Generate(
                request.eventType(),
                request.timeRange(),
                request.useThreadMode(),
                request.excludeNonJavaSamples(),
                request.excludeIdleSamples());

        flamegraphManager.save(generateRequest, request.flamegraphName(), request.useWeight());
    }

    @GET
    public List<GraphInfo> list() {
        return flamegraphManager.allCustom().stream()
                .sorted(Comparator.comparing(GraphInfo::createdAt).reversed())
                .toList();
    }

    @GET
    @Path("/events")
    public Map<String, EventSummaryResult> events() {
        return flamegraphManager.supportedEvents();
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

    @POST
    @Path("/{flamegraphId}/export")
    public void exportBytId(@PathParam("flamegraphId") String flamegraphId) {
        flamegraphManager.export(flamegraphId);
    }

    @POST
    @Path("/export")
    public void export(ExportRequest request) {
        flamegraphManager.export(request.eventType(), request.timeRange(), request.threadMode());
    }
}
