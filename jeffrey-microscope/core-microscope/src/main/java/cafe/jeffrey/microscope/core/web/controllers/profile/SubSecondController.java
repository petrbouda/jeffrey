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
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.resources.request.GetSubSecondRequest;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.ProfilingStartEnd;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import tools.jackson.databind.JsonNode;

import static cafe.jeffrey.microscope.core.web.controllers.profile.FlamegraphController.toTimeRange;

@RestController
@RequestMapping("/api/internal/profiles/{profileId}/subsecond")
public class SubSecondController {

    private static final Logger LOG = LoggerFactory.getLogger(SubSecondController.class);

    private static final int DEFAULT_BUCKET_SIZE_MS = 20;

    private final ProfileManagerResolver resolver;

    public SubSecondController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    @PostMapping
    public JsonNode generate(
            @PathVariable("profileId") String profileId,
            @RequestBody GetSubSecondRequest request) {
        LOG.debug("Generating sub-second analysis: eventType={}", request.eventType());
        ProfileManager pm = resolver.resolve(profileId);
        ProfileInfo profileInfo = pm.info();
        RelativeTimeRange relativeTimeRange = null;
        if (request.timeRange() != null) {
            ProfilingStartEnd startEnd = new ProfilingStartEnd(
                    profileInfo.profilingStartedAt(), profileInfo.profilingFinishedAt());
            relativeTimeRange = toTimeRange(request.timeRange()).toRelativeTimeRange(startEnd);
        }
        int bucketSizeMs = request.bucketSizeMs() != null ? request.bucketSizeMs() : DEFAULT_BUCKET_SIZE_MS;
        return pm.subSecondManager().generate(
                request.eventType(), request.useWeight(), relativeTimeRange, bucketSizeMs);
    }
}
