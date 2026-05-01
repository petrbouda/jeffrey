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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.resources.request.GetSubSecondRequest;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.ProfilingStartEnd;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import tools.jackson.databind.JsonNode;

import static cafe.jeffrey.microscope.core.web.controllers.profile.FlamegraphController.toTimeRange;

@RestController
@RequestMapping("/api/internal/profiles/{profileId}/subsecond")
public class SubSecondController {

    private static final Logger LOG = LoggerFactory.getLogger(SubSecondController.class);

    private final ProfileManagerResolver resolver;

    public SubSecondController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    @PostMapping
    public JsonNode generate(
            @PathVariable("profileId") String profileId,
            @RequestBody GetSubSecondRequest request) {
        LOG.debug("Generating sub-second analysis: eventType={}", request.eventType());
        ProfileManager pm = resolver.resolve(profileId);
        ProfileInfo profileInfo = pm.info();
        RelativeTimeRange relativeTimeRange = null;
        if (request.timeRange() != null) {
            ProfilingStartEnd startEnd = new ProfilingStartEnd(
                    profileInfo.profilingStartedAt(), profileInfo.profilingFinishedAt());
            relativeTimeRange = toTimeRange(request.timeRange()).toRelativeTimeRange(startEnd);
        }
        return pm.subSecondManager().generate(request.eventType(), request.useWeight(), relativeTimeRange);
    }
}
