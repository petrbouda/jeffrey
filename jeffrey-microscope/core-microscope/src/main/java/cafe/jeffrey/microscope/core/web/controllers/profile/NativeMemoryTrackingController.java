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
