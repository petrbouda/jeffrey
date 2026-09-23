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
