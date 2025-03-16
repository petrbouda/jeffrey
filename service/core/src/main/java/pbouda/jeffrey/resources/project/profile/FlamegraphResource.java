
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
import pbouda.jeffrey.TimeRangeRequest;
import pbouda.jeffrey.common.GraphType;
import pbouda.jeffrey.common.model.ProfilingStartEnd;
import pbouda.jeffrey.common.config.GraphParameters;
import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.common.model.time.TimeRange;
import pbouda.jeffrey.common.model.time.UndefinedTimeRange;
import pbouda.jeffrey.flamegraph.api.GraphData;
import pbouda.jeffrey.manager.FlamegraphManager;
import pbouda.jeffrey.model.EventSummaryResult;
import pbouda.jeffrey.resources.request.GenerateFlamegraphRequest;
import pbouda.jeffrey.timeseries.TimeseriesUtils;

import java.time.Instant;
import java.util.List;

public class FlamegraphResource {

    private final ProfileInfo profileInfo;
    private final FlamegraphManager flamegraphManager;

    public FlamegraphResource(ProfileInfo profileInfo, FlamegraphManager flamegraphManager) {
        this.profileInfo = profileInfo;
        this.flamegraphManager = flamegraphManager;
    }

    @Path("/repository")
    public FlamegraphRepositoryResource flamegraphRepositoryResource() {
        return new FlamegraphRepositoryResource(
                profileInfo, flamegraphManager.graphRepositoryManager(), GraphType.PRIMARY);
    }

    @POST
    public GraphData generate(GenerateFlamegraphRequest request) {
        Instant recordingStart = profileInfo.profilingStartedAt();

        GraphData data = flamegraphManager.generate(mapToGenerateRequest(profileInfo, request, GraphType.PRIMARY));
        /*
         * Current Timeseries graph counts on the fact that the timeseries data is available in absolute values
         * (not relative ones based on the recording start). We need to move the points to the absolute time.
         */
        if (data.timeseries() != null) {
            TimeseriesUtils.toAbsoluteTime(data.timeseries(), recordingStart.toEpochMilli());
        }

        return data;
    }

    @GET
    @Path("/events")
    public List<EventSummaryResult> events() {
        return flamegraphManager.eventSummaries();
    }

    static GraphParameters mapToGenerateRequest(
            ProfileInfo profileInfo, GenerateFlamegraphRequest request, GraphType graphType) {

        ProfilingStartEnd primaryStartEnd = new ProfilingStartEnd(
                profileInfo.profilingStartedAt(), profileInfo.profilingFinishedAt());

        RelativeTimeRange relativeTimeRange = toTimeRange(request.timeRange())
                .toRelativeTimeRange(primaryStartEnd);

        GraphParameters graphParameters = GraphParameters.builder()
                .withEventType(request.eventType())
                .withTimeRange(relativeTimeRange)
                .withThreadInfo(request.threadInfo())
                .withThreadMode(request.useThreadMode())
                .withUseWeight(request.useWeight())
                .withExcludeNonJavaSamples(request.excludeNonJavaSamples())
                .withExcludeIdleSamples(request.excludeIdleSamples())
                .withOnlyUnsafeAllocationSamples(request.onlyUnsafeAllocationSamples())
                .withParseLocation(true)
                .withGraphType(graphType)
                .withGraphComponents(request.components())
                .withSearchPattern(request.search())
                .withMarkers(request.markers())
                .build();

        return graphParameters;
    }

    public static TimeRange toTimeRange(TimeRangeRequest timeRangeRequest) {
        if (timeRangeRequest != null) {
            return TimeRange.create(
                    timeRangeRequest.start(),
                    timeRangeRequest.end(),
                    timeRangeRequest.absoluteTime());
        }
        return UndefinedTimeRange.INSTANCE;
    }
}
