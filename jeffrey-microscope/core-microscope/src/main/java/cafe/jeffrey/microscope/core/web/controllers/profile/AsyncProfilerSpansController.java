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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.common.config.GraphParameters;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.SpanManager;
import cafe.jeffrey.profile.manager.model.span.SpanDetailRow;
import cafe.jeffrey.profile.manager.model.span.SpanEventRow;
import cafe.jeffrey.profile.manager.model.span.SpanOverview;
import cafe.jeffrey.profile.manager.model.span.SpanSlowestRow;
import cafe.jeffrey.profile.manager.model.span.SpanTagStat;
import cafe.jeffrey.profile.model.FlamegraphPanel;
import cafe.jeffrey.profile.panel.JfrFlamegraphPanelProvider;
import cafe.jeffrey.profile.panel.PanelContext;
import cafe.jeffrey.profile.resources.request.GenerateSingleSpanFlamegraphRequest;
import cafe.jeffrey.profile.resources.request.GenerateSpanFlamegraphRequest;
import cafe.jeffrey.profile.resources.request.SpanFlamegraphOptions;
import cafe.jeffrey.shared.common.GraphType;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.ProfilingStartEnd;
import cafe.jeffrey.shared.common.model.SpanInterval;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import cafe.jeffrey.shared.common.model.time.UndefinedTimeRange;

import java.util.List;

@RestController
@RequestMapping("/api/internal/profiles/{profileId}/async-profiler")
public class AsyncProfilerSpansController {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncProfilerSpansController.class);

    private static final String DEFAULT_SLOWEST_LIMIT = "50";

    private final ProfileManagerResolver resolver;
    private final JfrFlamegraphPanelProvider panelProvider;

    public AsyncProfilerSpansController(ProfileManagerResolver resolver, JfrFlamegraphPanelProvider panelProvider) {
        this.resolver = resolver;
        this.panelProvider = panelProvider;
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
            @RequestParam("threadHash") long threadHash,
            @RequestParam("fromMillis") long fromMillis,
            @RequestParam("toMillis") long toMillis) {
        LOG.debug("Listing span events: profileId={} thread_hash={} from={} to={}",
                profileId, threadHash, fromMillis, toMillis);
        return mgr(profileId).spanEvents(threadHash, fromMillis, toMillis);
    }

    @GetMapping("/spans/panels")
    public List<FlamegraphPanel> spanPanels(
            @PathVariable("profileId") String profileId,
            @RequestParam("tag") String tag) {
        LOG.debug("Building span-scoped flamegraph panels: profileId={} tag={}", profileId, tag);
        ProfileManager pm = resolver.resolve(profileId);
        List<SpanInterval> intervals = pm.spanManager().tagIntervals(tag);
        return panelProvider.panels(pm.flamegraphManager().eventSummaries(intervals), PanelContext.PRIMARY);
    }

    @PostMapping(value = "/spans/flamegraph", produces = FlamegraphController.PROTOBUF_MEDIA_TYPE)
    public byte[] spanFlamegraph(
            @PathVariable("profileId") String profileId,
            @RequestBody GenerateSpanFlamegraphRequest request) {
        LOG.debug("Generating span-scoped flamegraph: profileId={} tag={} eventType={}",
                profileId, request.tag(), request.eventType());
        ProfileManager pm = resolver.resolve(profileId);
        List<SpanInterval> intervals = pm.spanManager().tagIntervals(request.tag());
        GraphParameters params = mapToSpanGraphParameters(pm.info(), request, intervals);
        return pm.flamegraphManager().generate(params);
    }

    @PostMapping(value = "/spans/single/flamegraph", produces = FlamegraphController.PROTOBUF_MEDIA_TYPE)
    public byte[] singleSpanFlamegraph(
            @PathVariable("profileId") String profileId,
            @RequestBody GenerateSingleSpanFlamegraphRequest request) {
        LOG.debug("Generating single-span flamegraph: profileId={} thread_hash={} from={} to={} eventType={}",
                profileId, request.threadHash(), request.fromMillis(), request.toMillis(), request.eventType());
        ProfileManager pm = resolver.resolve(profileId);
        List<SpanInterval> intervals = List.of(
                new SpanInterval(request.threadHash(), request.fromMillis(), request.toMillis()));
        GraphParameters params = mapToSpanGraphParameters(pm.info(), request, intervals);
        return pm.flamegraphManager().generate(params);
    }

    private static GraphParameters mapToSpanGraphParameters(
            ProfileInfo profileInfo, SpanFlamegraphOptions request, List<SpanInterval> intervals) {
        // Full-profile range so the timeseries can bucket over the whole timeline; the span intervals
        // (not the time range) are what scope the samples, so a null range would NPE the timeseries init.
        ProfilingStartEnd primaryStartEnd = new ProfilingStartEnd(
                profileInfo.profilingStartedAt(), profileInfo.profilingFinishedAt());
        RelativeTimeRange fullRange = UndefinedTimeRange.INSTANCE.toRelativeTimeRange(primaryStartEnd);

        return GraphParameters.builder()
                .withEventType(request.eventType())
                .withTimeRange(fullRange)
                .withThreadMode(request.useThreadMode())
                .withUseWeight(request.useWeight())
                .withExcludeNonJavaSamples(request.excludeNonJavaSamples())
                .withExcludeIdleSamples(request.excludeIdleSamples())
                .withOnlyUnsafeAllocationSamples(request.onlyUnsafeAllocationSamples())
                .withParseLocation(true)
                .withGraphType(GraphType.PRIMARY)
                .withGraphComponents(request.components())
                .withSpanIntervals(intervals)
                .build();
    }

    private SpanManager mgr(String profileId) {
        return resolver.resolve(profileId).spanManager();
    }
}
