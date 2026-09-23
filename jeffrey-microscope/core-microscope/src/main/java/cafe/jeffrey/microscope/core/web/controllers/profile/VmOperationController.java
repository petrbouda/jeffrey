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
import cafe.jeffrey.profile.manager.VmOperationManager;
import cafe.jeffrey.profile.manager.model.vmoperation.VmOperationStat;
import cafe.jeffrey.profile.manager.model.vmoperation.SafepointLatencyData;
import cafe.jeffrey.profile.manager.model.vmoperation.VmOverview;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;

@RestController
@RequestMapping("/api/internal/profiles/{profileId}/vm-operations")
public class VmOperationController {

    private static final Logger LOG = LoggerFactory.getLogger(VmOperationController.class);

    private final ProfileManagerResolver resolver;

    public VmOperationController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping("")
    public VmOverview overview(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching VM-operations overview");
        return mgr(profileId).overview();
    }

    @GetMapping("/operations")
    public List<VmOperationStat> vmOperations(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching VM operations");
        return mgr(profileId).vmOperations();
    }

    @GetMapping("/pauses-timeline")
    public TimeseriesData pausesTimeline(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching pause timeline");
        return mgr(profileId).pausesTimeline();
    }

    @GetMapping("/safepoint-timeline")
    public TimeseriesData safepointTimeline(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching time-to-safepoint timeline");
        return mgr(profileId).timeToSafepointTimeline();
    }

    @GetMapping("/safepoint-offenders")
    public SafepointLatencyData safepointOffenders(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching safepoint offenders");
        return mgr(profileId).safepointOffenders();
    }

    private VmOperationManager mgr(String profileId) {
        return resolver.resolve(profileId).vmOperationManager();
    }
}
