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
import cafe.jeffrey.profile.common.event.JITDeoptimizationEvent;
import cafe.jeffrey.profile.common.event.JITDeoptimizationMethodAggregate;
import cafe.jeffrey.profile.common.event.JITDeoptimizationReasonCount;
import cafe.jeffrey.profile.common.event.JITDeoptimizationStats;
import cafe.jeffrey.profile.manager.JITDeoptimizationManager;
import cafe.jeffrey.timeseries.SingleSerie;

import java.util.List;

@RestController
@RequestMapping("/api/internal/profiles/{profileId}/deoptimization")
public class JITDeoptimizationController {

    private static final Logger LOG = LoggerFactory.getLogger(JITDeoptimizationController.class);
    private static final int MAX_EVENTS = 200;
    private static final int MAX_TOP_METHODS = 20;

    private final ProfileManagerResolver resolver;

    public JITDeoptimizationController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping("/statistics")
    public JITDeoptimizationStats statistics(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching JIT deoptimization statistics: profileId={}", profileId);
        return mgr(profileId).statistics();
    }

    @GetMapping("/timeseries")
    public SingleSerie timeseries(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching JIT deoptimization timeseries: profileId={}", profileId);
        return mgr(profileId).timeseries();
    }

    @GetMapping("/events")
    public List<JITDeoptimizationEvent> events(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching JIT deoptimization events: profileId={} limit={}", profileId, MAX_EVENTS);
        return mgr(profileId).events(MAX_EVENTS);
    }

    @GetMapping("/top-methods")
    public List<JITDeoptimizationMethodAggregate> topMethods(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching JIT deoptimization top methods: profileId={} limit={}", profileId, MAX_TOP_METHODS);
        return mgr(profileId).topMethods(MAX_TOP_METHODS);
    }

    @GetMapping("/reason-distribution")
    public List<JITDeoptimizationReasonCount> reasonDistribution(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching JIT deoptimization reason distribution: profileId={}", profileId);
        return mgr(profileId).reasonDistribution();
    }

    private JITDeoptimizationManager mgr(String profileId) {
        return resolver.resolve(profileId).jitDeoptimizationManager();
    }
}
