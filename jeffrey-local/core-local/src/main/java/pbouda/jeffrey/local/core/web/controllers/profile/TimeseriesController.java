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

package pbouda.jeffrey.local.core.web.controllers.profile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pbouda.jeffrey.local.core.web.ProfileManagerResolver;
import pbouda.jeffrey.profile.common.config.GraphParameters;
import pbouda.jeffrey.profile.manager.TimeseriesManager;
import pbouda.jeffrey.profile.resources.request.GenerateTimeseriesRequest;
import pbouda.jeffrey.timeseries.TimeseriesData;

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
        GraphParameters graphParameters = GraphParameters.builder()
                .withSearchPattern(request.search())
                .withUseWeight(request.useWeight())
                .withExcludeNonJavaSamples(request.excludeNonJavaSamples())
                .withExcludeIdleSamples(request.excludeIdleSamples())
                .withOnlyUnsafeAllocationSamples(request.onlyUnsafeAllocationSamples())
                .withMarkers(request.markers())
                .build();

        return new TimeseriesManager.Generate(
                request.eventType(),
                graphParameters,
                request.threadInfo());
    }
}
