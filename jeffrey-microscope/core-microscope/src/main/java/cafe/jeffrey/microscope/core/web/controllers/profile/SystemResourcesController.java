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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.manager.SystemResourcesManager;
import cafe.jeffrey.profile.manager.model.system.SystemOverview;
import cafe.jeffrey.profile.manager.model.system.SystemProcessInfo;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;

@RestController
@RequestMapping("/api/internal/profiles/{profileId}/system")
public class SystemResourcesController {

    private static final Logger LOG = LoggerFactory.getLogger(SystemResourcesController.class);

    private final ProfileManagerResolver resolver;

    public SystemResourcesController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping
    public SystemOverview overview(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching system overview");
        return mgr(profileId).overview();
    }

    @GetMapping("/cpu/timeline")
    public TimeseriesData cpuTimeline(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching CPU timeline");
        return mgr(profileId).cpuTimeline();
    }

    @GetMapping("/network/interfaces")
    public List<String> networkInterfaces(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching network interfaces");
        return mgr(profileId).networkInterfaces();
    }

    @GetMapping("/network/timeline")
    public TimeseriesData networkTimeline(
            @PathVariable("profileId") String profileId,
            @RequestParam("networkInterface") String networkInterface) {
        LOG.debug("Fetching network timeline: interface={}", networkInterface);
        return mgr(profileId).networkTimeline(networkInterface);
    }

    @GetMapping("/context-switches/timeline")
    public TimeseriesData contextSwitchTimeline(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching context-switch timeline");
        return mgr(profileId).contextSwitchTimeline();
    }

    @GetMapping("/processes")
    public List<SystemProcessInfo> processes(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching host processes");
        return mgr(profileId).processes();
    }

    private SystemResourcesManager mgr(String profileId) {
        return resolver.resolve(profileId).systemResourcesManager();
    }
}
