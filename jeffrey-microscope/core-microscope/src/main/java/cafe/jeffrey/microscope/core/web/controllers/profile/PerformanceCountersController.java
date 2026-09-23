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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.manager.additional.AdditionalFilesManager;
import cafe.jeffrey.profile.manager.additional.PerfCounter;

import java.util.List;

@RestController
@RequestMapping("/api/internal/profiles/{profileId}/perfcounters")
public class PerformanceCountersController {

    private static final Logger LOG = LoggerFactory.getLogger(PerformanceCountersController.class);

    private final ProfileManagerResolver resolver;

    public PerformanceCountersController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping("/exists")
    public boolean exists(@PathVariable("profileId") String profileId) {
        LOG.debug("Checking performance counters existence");
        return mgr(profileId).performanceCountersExists();
    }

    @GetMapping
    public List<PerfCounter> performanceCounters(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching performance counters");
        return mgr(profileId).performanceCounters();
    }

    private AdditionalFilesManager mgr(String profileId) {
        return resolver.resolve(profileId).additionalFilesManager();
    }
}
