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
import cafe.jeffrey.profile.manager.SpanManager;
import cafe.jeffrey.profile.manager.model.span.SpanDetailRow;
import cafe.jeffrey.profile.manager.model.span.SpanEventRow;
import cafe.jeffrey.profile.manager.model.span.SpanHeatmap;
import cafe.jeffrey.profile.manager.model.span.SpanOverview;
import cafe.jeffrey.profile.manager.model.span.SpanSlowestRow;
import cafe.jeffrey.profile.manager.model.span.SpanTagStat;

import java.util.List;

@RestController
@RequestMapping("/api/internal/profiles/{profileId}/async-profiler")
public class AsyncProfilerSpansController {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncProfilerSpansController.class);

    private static final String DEFAULT_SLOWEST_LIMIT = "50";

    private final ProfileManagerResolver resolver;

    public AsyncProfilerSpansController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping("/spans/overview")
    public SpanOverview overview(@PathVariable("profileId") String profileId) {
        LOG.debug("Building span overview: profileId={}", profileId);
        return mgr(profileId).overview();
    }

    @GetMapping("/spans/tags")
    public List<SpanTagStat> tags(@PathVariable("profileId") String profileId) {
        LOG.debug("Listing span tag statistics: profileId={}", profileId);
        return mgr(profileId).tagStatistics();
    }

    @GetMapping("/spans/heatmap")
    public SpanHeatmap heatmap(@PathVariable("profileId") String profileId) {
        LOG.debug("Building span heatmap: profileId={}", profileId);
        return mgr(profileId).heatmap();
    }

    @GetMapping("/spans/tag")
    public List<SpanDetailRow> tagSpans(
            @PathVariable("profileId") String profileId,
            @RequestParam("tag") String tag) {
        LOG.debug("Listing spans for tag: profileId={} tag={}", profileId, tag);
        return mgr(profileId).tagSpans(tag);
    }

    @GetMapping("/spans/slowest")
    public List<SpanSlowestRow> slowestSpans(
            @PathVariable("profileId") String profileId,
            @RequestParam(value = "limit", defaultValue = DEFAULT_SLOWEST_LIMIT) int limit) {
        LOG.debug("Listing slowest spans: profileId={} limit={}", profileId, limit);
        return mgr(profileId).slowestSpans(limit);
    }

    @GetMapping("/spans/events")
    public List<SpanEventRow> spanEvents(
            @PathVariable("profileId") String profileId,
            @RequestParam("osThreadId") long osThreadId,
            @RequestParam("fromMillis") long fromMillis,
            @RequestParam("toMillis") long toMillis) {
        LOG.debug("Listing span events: profileId={} os_thread_id={} from={} to={}",
                profileId, osThreadId, fromMillis, toMillis);
        return mgr(profileId).spanEvents(osThreadId, fromMillis, toMillis);
    }

    private SpanManager mgr(String profileId) {
        return resolver.resolve(profileId).spanManager();
    }
}
