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
import cafe.jeffrey.profile.manager.custom.MethodTracingManager;
import cafe.jeffrey.profile.manager.custom.model.method.CumulationMode;
import cafe.jeffrey.profile.manager.custom.model.method.MethodTracingCumulatedData;
import cafe.jeffrey.profile.manager.custom.model.method.MethodTracingOverviewData;
import cafe.jeffrey.profile.manager.custom.model.method.MethodTracingSlowestData;

@RestController
@RequestMapping("/api/internal/profiles/{profileId}/method-tracing")
public class MethodTracingController {

    private static final Logger LOG = LoggerFactory.getLogger(MethodTracingController.class);

    private final ProfileManagerResolver resolver;

    public MethodTracingController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping("/overview")
    public MethodTracingOverviewData overview(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching method tracing overview");
        return mgr(profileId).overview();
    }

    @GetMapping("/slowest")
    public MethodTracingSlowestData slowest(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching method tracing slowest");
        return mgr(profileId).slowest();
    }

    @GetMapping("/cumulated")
    public MethodTracingCumulatedData cumulated(
            @PathVariable("profileId") String profileId,
            @RequestParam("mode") String mode) {
        LOG.debug("Fetching method tracing cumulated: mode={}", mode);
        return mgr(profileId).cumulated(CumulationMode.fromString(mode));
    }

    private MethodTracingManager mgr(String profileId) {
        return resolver.resolve(profileId).custom().methodTracingManager();
    }
}
