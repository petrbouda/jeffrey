/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.local.core.web.controllers.profile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.local.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.TimeRangeRequest;
import cafe.jeffrey.profile.common.config.GraphParameters;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.model.EventSummaryResult;
import cafe.jeffrey.profile.resources.request.GenerateFlamegraphRequest;
import cafe.jeffrey.shared.common.GraphType;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.ProfilingStartEnd;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import cafe.jeffrey.shared.common.model.time.TimeRange;
import cafe.jeffrey.shared.common.model.time.UndefinedTimeRange;

import java.util.List;

@RestController
@RequestMapping("/api/internal/profiles/{profileId}/flamegraph")
public class FlamegraphController {

    public static final String PROTOBUF_MEDIA_TYPE = "application/x-protobuf";

    private static final Logger LOG = LoggerFactory.getLogger(FlamegraphController.class);

    private final ProfileManagerResolver resolver;

    public FlamegraphController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    @PostMapping(produces = PROTOBUF_MEDIA_TYPE)
    public byte[] generate(
            @PathVariable("profileId") String profileId,
            @RequestBody GenerateFlamegraphRequest request) {
        LOG.debug("Generating flamegraph: eventType={} useThreadMode={}", request.eventType(), request.useThreadMode());
        ProfileManager pm = resolver.resolve(profileId);
        GraphParameters params = mapToGenerateRequest(pm.info(), request, GraphType.PRIMARY);
        return pm.flamegraphManager().generate(params);
    }

    @GetMapping("/events")
    public List<EventSummaryResult> events(@PathVariable("profileId") String profileId) {
        ProfileManager pm = resolver.resolve(profileId);
        var result = pm.flamegraphManager().eventSummaries();
        LOG.debug("Listed flamegraph event types: profileId={} count={}", profileId, result.size());
        return result;
    }

    static GraphParameters mapToGenerateRequest(
            ProfileInfo profileInfo, GenerateFlamegraphRequest request, GraphType graphType) {

        ProfilingStartEnd primaryStartEnd = new ProfilingStartEnd(
                profileInfo.profilingStartedAt(), profileInfo.profilingFinishedAt());

        RelativeTimeRange relativeTimeRange = toTimeRange(request.timeRange())
                .toRelativeTimeRange(primaryStartEnd);

        return GraphParameters.builder()
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
