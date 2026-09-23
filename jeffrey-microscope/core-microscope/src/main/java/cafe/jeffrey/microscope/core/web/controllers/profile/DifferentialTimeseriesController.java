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
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.TimeseriesManager;
import cafe.jeffrey.profile.resources.request.GenerateTimeseriesRequest;
import cafe.jeffrey.timeseries.TimeseriesData;

@RestController
@RequestMapping({
        "/api/internal/profiles/{primaryProfileId}/diff/{secondaryProfileId}/differential-timeseries",
        "/api/internal/workspaces/{workspaceId}/projects/{projectId}/profiles/{primaryProfileId}/diff/{secondaryProfileId}/differential-timeseries"
})
public class DifferentialTimeseriesController {

    private static final Logger LOG = LoggerFactory.getLogger(DifferentialTimeseriesController.class);

    private final ProfileManagerResolver resolver;

    public DifferentialTimeseriesController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    @PostMapping
    public TimeseriesData generate(
            @PathVariable("primaryProfileId") String primaryProfileId,
            @PathVariable("secondaryProfileId") String secondaryProfileId,
            @RequestBody GenerateTimeseriesRequest request) {
        LOG.debug("Generating diff timeseries: eventType={}", request.eventType());
        ProfileManager primary = resolver.resolve(primaryProfileId);
        ProfileManager secondary = resolver.resolve(secondaryProfileId);
        TimeseriesManager diffMgr = primary.diffTimeseriesManager(secondary);

        GraphParameters graphParameters = GraphParameters.builder()
                .withSearchPattern(request.search())
                .withUseWeight(request.useWeight())
                .withExcludeNonJavaSamples(request.excludeNonJavaSamples())
                .withExcludeIdleSamples(request.excludeIdleSamples())
                .withOnlyUnsafeAllocationSamples(request.onlyUnsafeAllocationSamples())
                .build();

        return diffMgr.timeseries(new TimeseriesManager.Generate(
                request.eventType(),
                graphParameters,
                request.threadInfo()));
    }
}
