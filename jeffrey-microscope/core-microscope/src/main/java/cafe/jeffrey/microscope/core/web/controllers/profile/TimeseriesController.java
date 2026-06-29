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

package cafe.jeffrey.microscope.core.web.controllers.profile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.TimeRangeRequest;
import cafe.jeffrey.profile.common.config.GraphParameters;
import cafe.jeffrey.profile.common.config.GraphParametersBuilder;
import cafe.jeffrey.profile.manager.TimeseriesManager;
import cafe.jeffrey.profile.resources.request.GenerateTimeseriesRequest;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.TimeseriesData;

@RestController
@RequestMapping("/api/internal/profiles/{profileId}/timeseries")
public class TimeseriesController {

    private static final Logger LOG = LoggerFactory.getLogger(TimeseriesController.class);

    private final ProfileManagerResolver resolver;

    public TimeseriesController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    @PostMapping
    public TimeseriesData generate(
            @PathVariable("profileId") String profileId,
            @RequestBody GenerateTimeseriesRequest request) {
        LOG.debug("Generating timeseries: eventType={}", request.eventType());
        return resolver.resolve(profileId).timeseriesManager().timeseries(mapToGenerateRequest(request));
    }

    private static TimeseriesManager.Generate mapToGenerateRequest(GenerateTimeseriesRequest request) {
        GraphParametersBuilder builder = GraphParameters.builder()
                .withSearchPattern(request.search())
                .withUseWeight(request.useWeight())
                .withExcludeNonJavaSamples(request.excludeNonJavaSamples())
                .withExcludeIdleSamples(request.excludeIdleSamples())
                .withOnlyUnsafeAllocationSamples(request.onlyUnsafeAllocationSamples())
                .withMarkers(request.markers());

        TimeRangeRequest timeRange = request.timeRange();
        if (timeRange != null) {
            builder.withTimeRange(new RelativeTimeRange(timeRange.start(), timeRange.end()));
        }

        return new TimeseriesManager.Generate(
                request.eventType(),
                builder.build(),
                request.threadInfo(),
                request.targetBuckets(),
                request.allEventTypes());
    }
}
