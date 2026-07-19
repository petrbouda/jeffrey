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

package cafe.jeffrey.microscope.core.web.controllers.profile.otlp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.common.otlp.OtlpEventCategory;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.model.EventSummaryResult;

import java.util.List;

/**
 * OTLP-format flamegraph endpoints. Mirrors {@link cafe.jeffrey.microscope.core.web.controllers.profile.FlamegraphController}'s
 * {@code /events} interface at an OTLP-specific path so the UI can pick a client by format. The
 * flamegraph generation itself stays on the shared generic endpoint (it is format-agnostic — it takes an
 * event-type code and reads the shared events table); only the event-summary discovery differs, because
 * the set of possible event types and their category mapping is format-specific.
 */
@RestController
@RequestMapping("/api/internal/profiles/{profileId}/otlp/flamegraph")
public class OtlpFlamegraphController {

    private static final Logger LOG = LoggerFactory.getLogger(OtlpFlamegraphController.class);

    private final ProfileManagerResolver resolver;

    public OtlpFlamegraphController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping("/events")
    public List<EventSummaryResult> events(@PathVariable("profileId") String profileId) {
        ProfileManager pm = resolver.resolve(profileId);
        List<EventSummaryResult> result = withCategories(pm.flamegraphManager().allEventSummaries());
        LOG.debug("Listed OTLP flamegraph event types: profileId={} count={}", profileId, result.size());
        return result;
    }

    static List<EventSummaryResult> withCategories(List<EventSummaryResult> summaries) {
        return summaries.stream()
                .map(summary -> summary.withCategory(OtlpEventCategory.resolve(summary.code()).name()))
                .toList();
    }
}
