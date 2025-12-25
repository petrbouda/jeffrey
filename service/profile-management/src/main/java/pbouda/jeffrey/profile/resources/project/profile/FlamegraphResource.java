
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
import pbouda.jeffrey.profile.TimeRangeRequest;
import pbouda.jeffrey.common.GraphType;
import pbouda.jeffrey.common.config.GraphParameters;
import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.common.model.ProfilingStartEnd;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.common.model.time.TimeRange;
import pbouda.jeffrey.common.model.time.UndefinedTimeRange;
import pbouda.jeffrey.profile.manager.FlamegraphManager;
import pbouda.jeffrey.profile.model.EventSummaryResult;
import pbouda.jeffrey.profile.resources.request.GenerateFlamegraphRequest;

import java.util.List;

/**
 * REST resource for flamegraph generation using Protocol Buffers.
 */
public class FlamegraphResource {

    private static final String PROTOBUF_MEDIA_TYPE = "application/x-protobuf";

    private final ProfileInfo profileInfo;
    private final FlamegraphManager flamegraphManager;

    public FlamegraphResource(ProfileInfo profileInfo, FlamegraphManager flamegraphManager) {
        this.profileInfo = profileInfo;
        this.flamegraphManager = flamegraphManager;
    }

    @POST
    @Produces(PROTOBUF_MEDIA_TYPE)
    public byte[] generate(GenerateFlamegraphRequest request) {
        GraphParameters params = mapToGenerateRequest(profileInfo, request, GraphType.PRIMARY);
        return flamegraphManager.generate(params);
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
