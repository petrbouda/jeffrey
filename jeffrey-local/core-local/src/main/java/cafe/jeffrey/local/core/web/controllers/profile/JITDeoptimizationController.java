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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.local.core.web.ProfileManagerResolver;
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
