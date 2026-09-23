/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import cafe.jeffrey.microscope.model.GraphType;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.ProfilingStartEnd;
import cafe.jeffrey.microscope.model.SpanInterval;
import cafe.jeffrey.microscope.model.SpanScope;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.microscope.model.time.UndefinedTimeRange;

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
        return panelProvider.panels(
                pm.flamegraphManager().eventSummaries(SpanScope.of(intervals)), PanelContext.PRIMARY);
    }

    @PostMapping(value = "/spans/flamegraph", produces = ProfileMediaTypes.PROTOBUF)
    public byte[] spanFlamegraph(
            @PathVariable("profileId") String profileId,
            @RequestBody GenerateSpanFlamegraphRequest request) {
        LOG.debug("Generating span-scoped flamegraph: profileId={} tag={} eventType={}",
                profileId, request.tag(), request.eventType());
        ProfileManager pm = resolver.resolve(profileId);
        List<SpanInterval> intervals = pm.spanManager().tagIntervals(request.tag());
        GraphParameters params = SpanScopedGraphParameters.of(pm.info(), request, SpanScope.of(intervals));
        return pm.flamegraphManager().generate(params);
    }

    @PostMapping(value = "/spans/single/flamegraph", produces = ProfileMediaTypes.PROTOBUF)
    public byte[] singleSpanFlamegraph(
            @PathVariable("profileId") String profileId,
            @RequestBody GenerateSingleSpanFlamegraphRequest request) {
        LOG.debug("Generating single-span flamegraph: profileId={} thread_hash={} from={} to={} eventType={}",
                profileId, request.threadHash(), request.fromMillis(), request.toMillis(), request.eventType());
        ProfileManager pm = resolver.resolve(profileId);
        List<SpanInterval> intervals = List.of(
                new SpanInterval(request.threadHash(), request.fromMillis(), request.toMillis()));
        GraphParameters params = SpanScopedGraphParameters.of(pm.info(), request, SpanScope.of(intervals));
        return pm.flamegraphManager().generate(params);
    }


    private SpanManager mgr(String profileId) {
        return resolver.resolve(profileId).spanManager();
    }
}
