/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import cafe.jeffrey.profile.common.config.GraphParameters;
import cafe.jeffrey.profile.manager.TimeseriesManager;
import cafe.jeffrey.profile.resources.request.GenerateTimeseriesRequest;
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
        GraphParameters graphParameters = GraphParameters.builder()
                .withSearchPattern(request.search())
                .withUseWeight(request.useWeight())
                .withExcludeNonJavaSamples(request.excludeNonJavaSamples())
                .withExcludeIdleSamples(request.excludeIdleSamples())
                .withOnlyUnsafeAllocationSamples(request.onlyUnsafeAllocationSamples())
                .build();

        return new TimeseriesManager.Generate(
                request.eventType(),
                graphParameters,
                request.threadInfo());
    }
}
