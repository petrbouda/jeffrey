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
import cafe.jeffrey.profile.manager.memory.NativeMemoryTrackingManager;
import cafe.jeffrey.profile.manager.model.nmt.NmtCategory;
import cafe.jeffrey.profile.manager.model.nmt.NmtOverview;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;

@RestController
@RequestMapping("/api/internal/profiles/{profileId}/nmt")
public class NativeMemoryTrackingController {

    private static final Logger LOG = LoggerFactory.getLogger(NativeMemoryTrackingController.class);

    private final ProfileManagerResolver resolver;

    public NativeMemoryTrackingController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping
    public NmtOverview overview(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching NMT overview");
        return mgr(profileId).overview();
    }

    @GetMapping("/categories")
    public List<NmtCategory> categories(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching NMT categories");
        return mgr(profileId).categories();
    }

    @GetMapping("/category-timeline")
    public TimeseriesData categoryTimeline(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching NMT category timeline");
        return mgr(profileId).categoryTimeline();
    }

    @GetMapping("/total-timeline")
    public TimeseriesData totalTimeline(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching NMT total timeline");
        return mgr(profileId).totalTimeline();
    }

    @GetMapping("/rss-vs-tracked")
    public TimeseriesData rssVsTracked(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching NMT RSS-vs-tracked timeline");
        return mgr(profileId).rssVsTrackedTimeline();
    }

    private NativeMemoryTrackingManager mgr(String profileId) {
        return resolver.resolve(profileId).nativeMemoryTrackingManager();
    }
}
