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
import pbouda.jeffrey.profile.manager.HeapMemoryManager;
import pbouda.jeffrey.profile.manager.model.heap.HeapMemoryOverviewData;
import pbouda.jeffrey.profile.manager.model.heap.HeapMemoryTimeseriesType;
import pbouda.jeffrey.timeseries.SingleSerie;

@RestController
@RequestMapping({
        "/api/internal/profiles/{profileId}/heap-memory",
        "/api/internal/quick-analysis/profiles/{profileId}/heap-memory",
        "/api/internal/workspaces/{workspaceId}/projects/{projectId}/profiles/{profileId}/heap-memory"
})
public class HeapMemoryController {

    private static final Logger LOG = LoggerFactory.getLogger(HeapMemoryController.class);

    private final ProfileManagerResolver resolver;

    public HeapMemoryController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping
    public HeapMemoryOverviewData overviewData(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching heap memory overview");
        return mgr(profileId).getOverviewData();
    }

    @GetMapping("/timeseries")
    public SingleSerie timeseries(
            @PathVariable("profileId") String profileId,
            @RequestParam("timeseriesType") HeapMemoryTimeseriesType timeseriesType) {
        LOG.debug("Fetching heap memory timeseries: type={}", timeseriesType);
        return mgr(profileId).timeseries(timeseriesType);
    }

    private HeapMemoryManager mgr(String profileId) {
        return resolver.resolve(profileId).heapMemoryManager();
    }
}
