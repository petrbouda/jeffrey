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
import cafe.jeffrey.profile.manager.StwTimelineManager;
import cafe.jeffrey.profile.manager.model.stw.StwEvent;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;

@RestController
@RequestMapping("/api/internal/profiles/{profileId}/stw")
public class StwTimelineController {

    private static final Logger LOG = LoggerFactory.getLogger(StwTimelineController.class);

    // Default floor for the per-event timeline: keep the payload to the pauses that matter (>= 1 ms).
    private static final long DEFAULT_MIN_DURATION_NANOS = 1_000_000L;

    private final ProfileManagerResolver resolver;

    public StwTimelineController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping("/timeline")
    public List<StwEvent> timeline(
            @PathVariable("profileId") String profileId,
            @RequestParam(value = "minDurationNanos", defaultValue = "" + DEFAULT_MIN_DURATION_NANOS) long minDurationNanos) {
        LOG.debug("Fetching STW timeline: minDurationNanos={}", minDurationNanos);
        return mgr(profileId).timeline(minDurationNanos);
    }

    @GetMapping("/budget")
    public TimeseriesData budget(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching STW app-stop budget");
        return mgr(profileId).budget();
    }

    private StwTimelineManager mgr(String profileId) {
        return resolver.resolve(profileId).stwTimelineManager();
    }
}
