
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
import pbouda.jeffrey.common.GraphType;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.common.config.GraphComponents;
import pbouda.jeffrey.common.config.GraphParameters;
import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.flamegraph.api.GraphData;
import pbouda.jeffrey.manager.GraphRepositoryManager;
import pbouda.jeffrey.manager.GraphRepositoryManager.GraphContentWithMetadata;
import pbouda.jeffrey.manager.GraphRepositoryManager.GraphMetadataWithGenerateRequest;
import pbouda.jeffrey.resources.request.GenerateFlamegraphRequest;
import pbouda.jeffrey.resources.util.InstantUtils;
import pbouda.jeffrey.timeseries.TimeseriesUtils;

import java.util.Comparator;
import java.util.List;

import static pbouda.jeffrey.resources.project.profile.FlamegraphResource.mapToGenerateRequest;

public class FlamegraphRepositoryResource {

    public record GraphMetadataResponse(
            String id,
            String name,
            Type eventType,
            boolean useWeight,
            boolean isPrimary,
            boolean withTimeseries,
            String createdAt) {
    }

    public record GraphDataResponse(GraphMetadataResponse metadata, GraphData content) {
    }

    private final ProfileInfo profileInfo;
    private final GraphRepositoryManager graphRepositoryManager;
    private final GraphType graphType;

    public FlamegraphRepositoryResource(
            ProfileInfo profileInfo, GraphRepositoryManager graphRepositoryManager, GraphType graphType) {

        this.profileInfo = profileInfo;
        this.graphRepositoryManager = graphRepositoryManager;
        this.graphType = graphType;
    }

    @POST
    public void saveRange(GenerateFlamegraphRequest request) {
        graphRepositoryManager.save(mapToGenerateRequest(profileInfo, request, graphType), request.flamegraphName());
    }

    @GET
    public List<GraphMetadataResponse> list() {
        return graphRepositoryManager.list().stream()
                .map(FlamegraphRepositoryResource::mapToGraphMetadataResponse)
                .sorted(Comparator.comparing((GraphMetadataResponse resp) -> resp.createdAt).reversed())
                .toList();
    }

    @GET
    @Path("/{graphId}")
    public GraphDataResponse getById(@PathParam("graphId") String graphId) {
        GraphContentWithMetadata savedGraphData = graphRepositoryManager.get(graphId)
                .orElseThrow(() -> new NotFoundException("Flamegraph not found"));

        GraphMetadataResponse metadataResponse = mapToGraphMetadataResponse(savedGraphData.metadata());

        /*
         * Current Timeseries graph counts on the fact that the timeseries data is available in absolute values
         * (not relative ones based on the recording start). We need to move the points to the absolute time.
         */
        if (metadataResponse.withTimeseries) {
            TimeseriesUtils.toAbsoluteTime(
                    savedGraphData.content().timeseries(),
                    profileInfo.profilingStartedAt().toEpochMilli());
        }

        return new GraphDataResponse(metadataResponse, savedGraphData.content());
    }

    @DELETE
    @Path("/{graphId}")
    public void delete(@PathParam("graphId") String flamegraphId) {
        graphRepositoryManager.delete(flamegraphId);
    }

    private static GraphMetadataResponse mapToGraphMetadataResponse(GraphMetadataWithGenerateRequest metadata) {
        GraphParameters graphParameters = metadata.graphParameters();
        return new GraphMetadataResponse(
                metadata.id(),
                metadata.name(),
                graphParameters.eventType(),
                graphParameters.useWeight(),
                graphParameters.graphType() == GraphType.PRIMARY,
                graphParameters.graphComponents() != GraphComponents.FLAMEGRAPH_ONLY,
                InstantUtils.formatInstant(metadata.createdAt()));
    }
}
