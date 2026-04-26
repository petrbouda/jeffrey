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

package pbouda.jeffrey.local.core.web.controllers.profile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pbouda.jeffrey.local.core.web.ProfileManagerResolver;
import pbouda.jeffrey.profile.manager.GarbageCollectionManager;
import pbouda.jeffrey.profile.manager.model.gc.GCOverviewData;
import pbouda.jeffrey.profile.manager.model.gc.GCTimeseriesType;
import pbouda.jeffrey.profile.manager.model.gc.configuration.GCConfigurationData;
import pbouda.jeffrey.timeseries.TimeseriesData;

@RestController
@RequestMapping({
        "/api/internal/profiles/{profileId}/gc",
        "/api/internal/quick-analysis/profiles/{profileId}/gc",
        "/api/internal/workspaces/{workspaceId}/projects/{projectId}/profiles/{profileId}/gc"
})
public class GarbageCollectionController {

    private static final Logger LOG = LoggerFactory.getLogger(GarbageCollectionController.class);

    private final ProfileManagerResolver resolver;

    public GarbageCollectionController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping
    public GCOverviewData overviewData(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching GC overview");
        return mgr(profileId).overviewData();
    }

    @GetMapping("/timeseries")
    public TimeseriesData timeseries(
            @PathVariable("profileId") String profileId,
            @RequestParam("timeseriesType") GCTimeseriesType timeseriesType) {
        LOG.debug("Fetching GC timeseries: type={}", timeseriesType);
        return mgr(profileId).timeseries(timeseriesType);
    }

    @GetMapping("/configuration")
    public GCConfigurationData configuration(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching GC configuration");
        return mgr(profileId).configuration();
    }

    private GarbageCollectionManager mgr(String profileId) {
        return resolver.resolve(profileId).gcManager();
    }
}
