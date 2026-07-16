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

package cafe.jeffrey.microscope.core.web.controllers.profile.pprof;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.manager.FlamegraphManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.model.EventSummaryResult;

import java.util.List;

import static cafe.jeffrey.microscope.core.web.controllers.profile.pprof.PprofFlamegraphController.withCategories;

/**
 * pprof-format differential flamegraph endpoints. Mirrors
 * {@link cafe.jeffrey.microscope.core.web.controllers.profile.DifferentialFlamegraphController}'s
 * {@code /events} interface at a pprof-specific path, tagging each event summary with its pprof
 * category so the differential flamegraph UI groups cards the same way the primary view does.
 */
@RestController
@RequestMapping("/api/internal/profiles/{primaryProfileId}/pprof/diff/{secondaryProfileId}/differential-flamegraph")
public class PprofDifferentialFlamegraphController {

    private static final Logger LOG = LoggerFactory.getLogger(PprofDifferentialFlamegraphController.class);

    private final ProfileManagerResolver resolver;

    public PprofDifferentialFlamegraphController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping("/events")
    public List<EventSummaryResult> events(
            @PathVariable("primaryProfileId") String primaryProfileId,
            @PathVariable("secondaryProfileId") String secondaryProfileId) {
        ProfileManager primary = resolver.resolve(primaryProfileId);
        ProfileManager secondary = resolver.resolve(secondaryProfileId);
        FlamegraphManager diffManager = primary.diffFlamegraphManager(secondary);
        List<EventSummaryResult> result = withCategories(diffManager.allEventSummaries());
        LOG.debug("Listed pprof diff flamegraph event types: profileId={} count={}", primaryProfileId, result.size());
        return result;
    }
}
