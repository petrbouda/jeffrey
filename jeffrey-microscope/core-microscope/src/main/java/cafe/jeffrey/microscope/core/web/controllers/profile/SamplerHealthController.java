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

import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.provider.profile.api.CpuTimeSampleLoss;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What the samplers themselves reported about the completeness of their own output. Read by the
 * views that draw those samples, so a graph built on partial data can say so.
 */
@RestController
@RequestMapping("/api/internal/profiles/{profileId}/sampler-health")
public class SamplerHealthController {

    private static final Logger LOG = LoggerFactory.getLogger(SamplerHealthController.class);

    private final ProfileManagerResolver resolver;

    public SamplerHealthController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping("/cpu-time-sample-loss")
    public CpuTimeSampleLoss cpuTimeSampleLoss(@PathVariable("profileId") String profileId) {
        ProfileManager pm = resolver.resolve(profileId);
        CpuTimeSampleLoss loss = pm.samplerHealthManager().cpuTimeSampleLoss();
        LOG.debug("Resolved CPU-time sample loss: profileId={} captured={} lost={} loss_events={}",
                profileId, loss.capturedSamples(), loss.lostSamples(), loss.lossEvents());
        return loss;
    }
}
