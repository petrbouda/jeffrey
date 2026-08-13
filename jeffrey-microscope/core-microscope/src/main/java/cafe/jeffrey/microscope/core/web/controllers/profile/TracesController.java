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

import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.common.config.GraphParameters;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.model.FlamegraphPanel;
import cafe.jeffrey.profile.panel.JfrFlamegraphPanelProvider;
import cafe.jeffrey.profile.panel.PanelContext;
import cafe.jeffrey.profile.manager.model.trace.TraceEventRow;
import cafe.jeffrey.profile.manager.model.trace.TraceDetail;
import cafe.jeffrey.profile.manager.model.trace.TraceOperationRow;
import cafe.jeffrey.profile.manager.model.trace.TraceOverview;
import cafe.jeffrey.profile.manager.model.trace.TraceRow;
import cafe.jeffrey.profile.resources.request.GenerateTraceSpanFlamegraphRequest;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.model.SpanInterval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Serves the trace views: the list of traces, one trace's span tree, and what the JVM was doing
 * inside a single span.
 * <p>
 * Trace and span ids travel as 16-char hex strings, because a 64-bit id exceeds JavaScript's safe
 * integer range and would be silently rounded as a JSON number.
 * <p>
 * Kept independent of the async-profiler span feature. {@code profiler.Span} is a flat, per-thread
 * latency interval selected by tag; a trace span is a node in a tree. They answer different
 * questions and share only the interval-scoped flamegraph machinery, which lives in
 * {@link SpanScopedGraphParameters} so neither feature depends on the other.
 */
@RestController
@RequestMapping("/api/internal/profiles/{profileId}/traces")
public class TracesController {

    private static final Logger LOG = LoggerFactory.getLogger(TracesController.class);

    private static final String DEFAULT_TRACES_LIMIT = "100";
    private static final String DEFAULT_OPERATIONS_LIMIT = "100";

    private final ProfileManagerResolver resolver;
    private final JfrFlamegraphPanelProvider panelProvider;

    public TracesController(ProfileManagerResolver resolver, JfrFlamegraphPanelProvider panelProvider) {
        this.resolver = resolver;
        this.panelProvider = panelProvider;
    }

    @GetMapping
    public List<TraceRow> traces(
            @PathVariable("profileId") String profileId,
            @RequestParam(value = "limit", defaultValue = DEFAULT_TRACES_LIMIT) int limit) {
        LOG.debug("Listing slowest traces: profileId={} limit={}", profileId, limit);
        return resolver.resolve(profileId).traceManager().slowestTraces(limit);
    }

    /**
     * Profile-wide totals for the summary above the trace list. Mapped before {@code /{traceId}} so
     * the literal path is the obvious one to read, not merely the one Spring happens to prefer.
     */
    @GetMapping("/overview")
    public TraceOverview overview(@PathVariable("profileId") String profileId) {
        LOG.debug("Reading trace overview: profileId={}", profileId);
        return resolver.resolve(profileId).traceManager().overview();
    }

    @GetMapping("/operations")
    public List<TraceOperationRow> operations(
            @PathVariable("profileId") String profileId,
            @RequestParam(value = "limit", defaultValue = DEFAULT_OPERATIONS_LIMIT) int limit) {
        LOG.debug("Aggregating trace operations: profileId={} limit={}", profileId, limit);
        return resolver.resolve(profileId).traceManager().operations(limit);
    }

    @GetMapping("/{traceId}")
    public TraceDetail trace(
            @PathVariable("profileId") String profileId,
            @PathVariable("traceId") String traceId) {
        LOG.debug("Loading trace: profileId={} trace_id={}", profileId, traceId);
        return resolver.resolve(profileId).traceManager()
                .trace(parseId(traceId))
                .orElseThrow(() -> Exceptions.resourceNotFound("Trace not found: " + traceId));
    }

    /**
     * The events that ran on the span's thread while it was open — the "what was the JVM doing in
     * here" breakdown. Reuses the async-profiler span machinery, which already answers exactly this
     * question for a (thread, window) pair.
     */
    @GetMapping("/{traceId}/spans/{spanId}/events")
    public List<TraceEventRow> spanEvents(
            @PathVariable("profileId") String profileId,
            @PathVariable("traceId") String traceId,
            @PathVariable("spanId") String spanId) {
        LOG.debug("Listing events inside a span: profileId={} trace_id={} span_id={}",
                profileId, traceId, spanId);
        return resolver.resolve(profileId).traceManager()
                .eventsInSpan(parseId(traceId), parseId(spanId));
    }

    /**
     * The flamegraph cards available for one span — which event types actually recorded anything
     * inside it, with the real counts, so the drill-down offers only graphs that exist.
     */
    @GetMapping("/{traceId}/spans/{spanId}/panels")
    public List<FlamegraphPanel> spanPanels(
            @PathVariable("profileId") String profileId,
            @PathVariable("traceId") String traceId,
            @PathVariable("spanId") String spanId,
            @RequestParam(value = "selfOnly", defaultValue = "false") boolean selfOnly) {
        LOG.debug("Building span-scoped flamegraph panels: profileId={} trace_id={} span_id={} self_only={}",
                profileId, traceId, spanId, selfOnly);
        ProfileManager profileManager = resolver.resolve(profileId);

        List<SpanInterval> intervals = profileManager.traceManager()
                .spanIntervals(parseId(traceId), parseId(spanId), selfOnly);
        if (intervals.isEmpty()) {
            return List.of();
        }
        return panelProvider.panels(
                profileManager.flamegraphManager().eventSummaries(intervals), PanelContext.PRIMARY);
    }

    /**
     * A flamegraph of the samples taken while one span was running — the whole point of keeping
     * traces in the same recording as the profile.
     */
    @PostMapping(value = "/{traceId}/spans/{spanId}/flamegraph",
            produces = FlamegraphController.PROTOBUF_MEDIA_TYPE)
    public byte[] spanFlamegraph(
            @PathVariable("profileId") String profileId,
            @PathVariable("traceId") String traceId,
            @PathVariable("spanId") String spanId,
            @RequestBody GenerateTraceSpanFlamegraphRequest request) {
        LOG.debug("Generating span flamegraph: profileId={} trace_id={} span_id={} self_only={}",
                profileId, traceId, spanId, request.selfOnly());
        ProfileManager profileManager = resolver.resolve(profileId);

        List<SpanInterval> intervals = profileManager.traceManager()
                .spanIntervals(parseId(traceId), parseId(spanId), request.selfOnly());
        if (intervals.isEmpty()) {
            throw Exceptions.resourceNotFound("Span has no samples to show: " + spanId);
        }

        GraphParameters params = SpanScopedGraphParameters.of(profileManager.info(), request, intervals);
        return profileManager.flamegraphManager().generate(params);
    }

    /**
     * Ids arrive as unsigned 16-char hex, so the full 64-bit range round-trips — including the half
     * of it that is negative as a signed {@code long}.
     */
    private static long parseId(String hex) {
        try {
            return Long.parseUnsignedLong(hex, 16);
        } catch (NumberFormatException e) {
            throw Exceptions.resourceNotFound("Malformed id: " + hex);
        }
    }
}
