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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies;
import cafe.jeffrey.microscope.core.mcp.MicroscopeView;
import cafe.jeffrey.microscope.core.mcp.UiLinks;
import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;
import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.profile.common.config.GraphParameters;
import cafe.jeffrey.profile.feature.FeatureType;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.TimeseriesManager;
import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.profile.mcp.McpNextTool;
import cafe.jeffrey.profile.mcp.McpToolCost;
import cafe.jeffrey.profile.mcp.McpToolMeta;
import cafe.jeffrey.profile.mcp.ToolParamBounds;
import cafe.jeffrey.timeseries.SingleSerie;
import tools.jackson.databind.JsonNode;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * When something happened, rather than where.
 * <p>
 * Every other tool that takes a window — {@code flamegraph_export}, {@code compare_flamegraph}, the
 * trace exports — accepts {@code startEpochMs} and {@code endEpochMs} and offers no way to choose
 * them. A flamegraph of a whole recording flattens a thirty-second spike into a five-minute average
 * and the spike stops being visible. This family picks the window; the flamegraph then explains it.
 * <p>
 * <strong>The reduction is the product, not the series.</strong> The managers behind this return
 * {@link SingleSerie}, which is chart geometry — {@code [[timestamp, value], …]}, three hundred
 * points for a five-minute recording at one-second resolution and thirty thousand at ten
 * milliseconds. Handing that to a model spends a large part of the output budget on a curve it
 * cannot act on, which is why the dashboards drop their series entirely. What is useful is the
 * <em>shape</em>: which windows carry the mass, ranked, each with the bounds to pass straight to the
 * next tool, plus a coarse profile line so a steady load, a sawtooth, a ramp and a single spike are
 * distinguishable at a glance. A raw series must never leave this class.
 * <p>
 * Every bound this class hands out is an instant on the recording's clock, UTC epoch milliseconds,
 * the base the window inputs of every other tool take.
 */
public class TimelineMcpTools {

    private static final MicroscopeView TIMESERIES_VIEW = MicroscopeView.FLAMEGRAPHS_PRIMARY;
    private static final MicroscopeView SUBSECOND_VIEW = MicroscopeView.SUBSECOND_VIEW;
    private static final String EVENT_TYPE_PARAM = "eventType";
    private static final String GRAPH_MODE_PARAM = "graphMode";
    private static final String PRIMARY_GRAPH_MODE = "PRIMARY";

    private static final int DEFAULT_TOP_WINDOWS = 5;
    private static final int MAX_TOP_WINDOWS = 25;

    /** How wide the shape line is. Wide enough to read a trend, narrow enough to stay one line. */
    private static final int SHAPE_CELLS = 40;
    private static final char[] SHAPE_RAMP = {' ', '.', ':', '-', '=', '+', '*', '#', '%', '@'};

    private static final int MAX_ZOOM_BUCKETS = 200;
    private static final int DEFAULT_ZOOM_BUCKET_MS = 20;
    private static final int MIN_ZOOM_BUCKET_MS = 1;
    private static final long MILLIS_PER_SECOND = 1000L;

    /** The heatmap's rows; each row's cells are under {@code data}. */
    private static final String SUBSECOND_SERIES_FIELD = "series";
    private static final String SUBSECOND_DATA_FIELD = "data";
    private static final String SUBSECOND_NAME_FIELD = "name";
    private static final String SUBSECOND_X_FIELD = "x";
    private static final String SUBSECOND_Y_FIELD = "y";

    private static final String START_INPUT = "startEpochMs";
    private static final String END_INPUT = "endEpochMs";

    /** The tools and arguments this family's answers hand back as next calls. */
    private static final String EXPORT_TOOL = "flamegraph_export";
    private static final String LIST_TOOL = "flamegraph_list";
    private static final String ZOOM_TOOL = "timeline_zoom";
    private static final String HOT_WINDOWS_TOOL = "timeline_hotWindows";
    private static final String PROFILE_ID = "profileId";
    private static final String EVENT_TYPE = "eventType";
    private static final String USE_WEIGHT = "useWeight";
    private static final String BUCKET_MS = "bucketMs";

    private static final String NO_TIMESERIES_DATA =
            "This profile carries no time-resolved data. pprof and OTLP imports are aggregated and hold "
                    + "no per-sample timestamps, so there is no timeline to draw - every sample is "
                    + "attributed to the whole recording at once.";

    private static final String NO_RECORDING_SPAN =
            "This profile carries no recording start and end, so no bucket can be placed on the "
                    + "recording's clock.";

    private static final String NOTHING_RECORDED =
            "No samples of '%s' were recorded, so there is no timeline for it.";

    private static final String EXPORT_WINDOW_WHY =
            "graphs only what happened inside the busiest window - the frames a whole-recording export averages away";
    private static final String EXPORT_WHOLE_WHY =
            "graphs the whole recording, which is all an aggregated import can be read as";
    private static final String ZOOM_WHY = "resolves the busiest window below one second";
    private static final String WIDER_WHY = "covers the whole recording, if this window is not the interesting one";
    private static final String LIST_WHY = "names the event types this profile actually captured";

    /** Why a timeline answer has nothing to rank. */
    enum TimelineStatus {
        /** The windows are ranked below. */
        OK,
        /** The profile carries no per-sample timestamps, so there is no timeline at all. */
        NO_TIMESERIES,
        /** Nothing of this event type landed in the recording, or in the window asked about. */
        NOT_RECORDED
    }

    private final ProfileManager profileManager;
    private final AdvertisedFamilies advertised;

    public TimelineMcpTools(ProfileManager profileManager, AdvertisedFamilies advertised) {
        this.profileManager = profileManager;
        this.advertised = advertised;
    }

    @Tool(description = "Reports when the samples of one event type actually landed: the recording "
            + "split into buckets, the busiest windows ranked, and a coarse shape line. A "
            + "whole-recording flamegraph averages a spike away; the startEpochMs and endEpochMs of "
            + "a window here are what make a flamegraph export show it. Every bound is UTC epoch "
            + "milliseconds. Answers 'when did the allocation happen', 'was this load steady or a "
            + "burst', 'which minute was the bad one'. status NO_TIMESERIES: the profile has no "
            + "timeline; NOT_RECORDED: no samples of this event type.")
    @McpOutputSchema(Timeline.class)
    @McpToolMeta(cost = McpToolCost.MODERATE)
    public McpToolResult hotWindows(
            @ToolParam(required = true, description = "Event type to profile over time, e.g. "
                    + "'jdk.ExecutionSample' or 'jdk.ObjectAllocationSample'. flamegraph_list names "
                    + "what this profile recorded.")
            String eventType,
            @ToolParam(required = false, description = "Weigh buckets by the event's weight (bytes allocated, nanoseconds "
                    + "blocked) instead of by sample count")
            Boolean useWeight,
            @ToolParam(required = false, description = "How many windows to rank (default " + DEFAULT_TOP_WINDOWS
                    + ", maximum " + MAX_TOP_WINDOWS + ")")
            @ToolParamBounds(defaultValue = DEFAULT_TOP_WINDOWS, min = 1, max = MAX_TOP_WINDOWS)
            Integer top) {

        Type type = FlamegraphMcpTools.requireEventType(eventType);
        boolean weighted = Boolean.TRUE.equals(useWeight);
        Answer answer = new Answer(type, weighted, UiLinks.view(profileId(), TIMESERIES_VIEW));
        if (timelineUnavailable()) {
            return answer.noTimeseries(NO_TIMESERIES_DATA);
        }
        Optional<RecordingSpan> span = RecordingSpan.of(profileManager.info());
        if (span.isEmpty()) {
            return answer.noTimeseries(NO_RECORDING_SPAN);
        }

        RecordingSpan recording = span.get();
        EpochWindow whole = recording.whole();
        SingleSerie serie = primarySerie(type, weighted);
        if (serie == null || serie.data().isEmpty()) {
            return answer.notRecorded(whole,
                    NextCalls.to(LIST_TOOL).with(PROFILE_ID, profileId()).why(LIST_WHY));
        }

        List<Bucket> buckets = buckets(serie, recording);
        List<Window> windows = topWindows(buckets, MILLIS_PER_SECOND, recording,
                ToolArguments.boundedLimit(top, DEFAULT_TOP_WINDOWS, MAX_TOP_WINDOWS));
        McpFollowUp followUp = windows.isEmpty()
                ? NextSteps.builder(advertised).followUp()
                : NextSteps.builder(advertised)
                        .next(export(type, weighted, windows.getFirst()).why(EXPORT_WINDOW_WHY))
                        .next(zoom(type, windows.getFirst()).why(ZOOM_WHY))
                        .followUp();
        return answer.ranked(whole, buckets, MILLIS_PER_SECOND, windows, followUp);
    }

    @Tool(description = "Returns the same shape at sub-second resolution inside one window - the only "
            + "view that resolves below a second: the first seconds of a startup, where one-second "
            + "buckets hide everything, or the inside of a spike timeline_hotWindows found. The "
            + "window and every bucket are UTC epoch milliseconds; a window outside the recording is "
            + "refused with the recording's span. The bucket count is capped, so a narrower window, "
            + "not wider buckets, gives the finer view.")
    @McpOutputSchema(Timeline.class)
    @McpToolMeta(cost = McpToolCost.MODERATE)
    public McpToolResult zoom(
            @ToolParam(required = true, description = "Event type to profile, e.g. 'jdk.ExecutionSample'")
            String eventType,
            @ToolParam(required = true, description = "Start of the window, as UTC epoch milliseconds inside the "
                    + "recording, e.g. a startEpochMs from timeline_hotWindows")
            Long startEpochMs,
            @ToolParam(required = true, description = "End of the window, as UTC epoch milliseconds inside the recording")
            Long endEpochMs,
            @ToolParam(required = false, description = "Bucket width in milliseconds (default "
                    + DEFAULT_ZOOM_BUCKET_MS + "). Below one second is the point of this tool; a window that "
                    + "would need more than " + MAX_ZOOM_BUCKETS + " buckets is refused rather than silently "
                    + "coarsened.")
            @ToolParamBounds(defaultValue = DEFAULT_ZOOM_BUCKET_MS, min = MIN_ZOOM_BUCKET_MS)
            Integer bucketMs) {

        Type type = FlamegraphMcpTools.requireEventType(eventType);
        Answer answer = new Answer(type, false, UiLinks.view(profileId(), SUBSECOND_VIEW, subsecondQuery(type)));
        if (timelineUnavailable()) {
            return answer.noTimeseries(NO_TIMESERIES_DATA);
        }

        long start = requireBound(startEpochMs, START_INPUT);
        long end = requireBound(endEpochMs, END_INPUT);
        // The window is required, so a recording with no span answers with why, not with advice to
        // leave the window out.
        Optional<RecordingSpan> span = RecordingSpan.of(profileManager.info());
        if (span.isEmpty()) {
            return answer.noTimeseries(NO_RECORDING_SPAN);
        }
        RecordingSpan recording = span.get();
        EpochWindow window = recording.window(start, end);

        int width = bucketMs == null ? DEFAULT_ZOOM_BUCKET_MS : bucketMs;
        if (width < MIN_ZOOM_BUCKET_MS) {
            throw new IllegalArgumentException("bucketMs must be at least " + MIN_ZOOM_BUCKET_MS);
        }
        long length = window.endEpochMs() - window.startEpochMs();
        long wanted = length / width;
        if (wanted > MAX_ZOOM_BUCKETS) {
            throw new IllegalArgumentException(
                    "A " + length + " ms window at " + width + " ms buckets needs " + wanted
                            + " buckets, more than the " + MAX_ZOOM_BUCKETS + " cap. Narrow the window "
                            + "or widen the buckets.");
        }

        List<Bucket> buckets = subSecondBuckets(type, recording, window, width);
        McpNextTool wider = NextCalls.to(HOT_WINDOWS_TOOL)
                .with(PROFILE_ID, profileId()).with(EVENT_TYPE, type.code()).why(WIDER_WHY);
        if (buckets.isEmpty()) {
            return answer.notRecorded(window, wider);
        }

        List<Window> windows = topWindows(buckets, width, recording, DEFAULT_TOP_WINDOWS);
        McpFollowUp followUp = NextSteps.builder(advertised)
                .next(export(type, false, windows.getFirst()).why(EXPORT_WINDOW_WHY))
                .next(wider)
                .followUp();
        return answer.ranked(window, buckets, width, windows, followUp);
    }

    /**
     * Both features travel together: they are disabled for the same reason, an import with no
     * per-sample timestamps.
     */
    private boolean timelineUnavailable() {
        return DashboardFeature.missing(profileManager, FeatureType.TIMESERIES);
    }

    private SingleSerie primarySerie(Type type, boolean useWeight) {
        GraphParameters parameters = GraphParameters.builder()
                .withEventType(type)
                .withTimeRange(FlamegraphMcpTools.timeRange(profileManager.info(), null))
                .withUseWeight(useWeight)
                .build();

        List<SingleSerie> series =
                profileManager.timeseriesManager()
                        .timeseries(new TimeseriesManager.Generate(type, parameters, null))
                        .series();
        return series.isEmpty() ? null : series.getFirst();
    }

    /**
     * The series as {@code (startEpochMs, value)} pairs. This is the only place the raw shape is read,
     * and nothing downstream of it returns one.
     * <p>
     * The series is keyed by <em>second</em> from the start of the recording - {@code
     * SecondValueTimeseriesBuilder} adds to {@code record.second()}, and the frontend multiplies by a
     * thousand before plotting. It is placed on the recording's clock here, once, because every bound
     * this class hands out is fed to a tool that reads epoch milliseconds: leaving it in seconds would
     * produce windows decades too early and a flamegraph of the wrong instant.
     */
    private static List<Bucket> buckets(SingleSerie serie, RecordingSpan recording) {
        List<Bucket> buckets = new ArrayList<>(serie.data().size());
        for (List<Long> point : serie.data()) {
            if (point.size() >= 2 && point.get(0) != null && point.get(1) != null) {
                buckets.add(new Bucket(recording.epochAt(point.getFirst() * MILLIS_PER_SECOND), point.get(1)));
            }
        }
        return buckets;
    }

    /**
     * Genuine sub-second buckets, from the heatmap the subsecond view is drawn from rather than from
     * the one-second series - re-aggregating that could not invent detail it does not carry, which is
     * the whole reason this tool exists.
     * <p>
     * The heatmap is a matrix: one row per offset within a second, one cell per second, counted from
     * the start of the window it was asked for. Flattening it back onto the recording's clock is what
     * turns it into something rankable.
     */
    private List<Bucket> subSecondBuckets(Type type, RecordingSpan recording, EpochWindow window, int width) {
        JsonNode model = profileManager.subSecondManager()
                .generate(type, false, recording.offsets(window), width);

        JsonNode rows = model.get(SUBSECOND_SERIES_FIELD);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        List<Bucket> buckets = new ArrayList<>();
        for (JsonNode row : rows) {
            long offsetMs = Long.parseLong(row.get(SUBSECOND_NAME_FIELD).asString());
            for (JsonNode cell : row.get(SUBSECOND_DATA_FIELD)) {
                long value = cell.get(SUBSECOND_Y_FIELD).asLong();
                if (value > 0) {
                    long second = Long.parseLong(cell.get(SUBSECOND_X_FIELD).asString()) - 1;
                    buckets.add(new Bucket(window.startEpochMs() + second * MILLIS_PER_SECOND + offsetMs, value));
                }
            }
        }
        buckets.sort(Comparator.comparingLong(Bucket::startEpochMs));
        return buckets;
    }

    private static long total(List<Bucket> buckets) {
        return buckets.stream().mapToLong(Bucket::value).sum();
    }

    /**
     * The windows carrying the most, each with the bounds the next tool takes. Ranked by value, not by
     * time: the point is to find the one worth graphing. A window never ends past the recording, so
     * every one of them is a window {@code flamegraph_export} accepts.
     */
    private static List<Window> topWindows(List<Bucket> buckets, long width, RecordingSpan recording, int top) {
        long total = Math.max(total(buckets), 1);
        return buckets.stream()
                .filter(bucket -> bucket.value() > 0)
                .sorted(Comparator.comparingLong(Bucket::value).reversed())
                .limit(top)
                .map(bucket -> new Window(
                        bucket.startEpochMs(),
                        endOf(bucket, width, recording),
                        bucket.value(),
                        Math.round(bucket.value() * 1000.0 / total) / 10.0))
                .toList();
    }

    /** A bucket's end, cut at the recording's end when the recording stopped inside the bucket. */
    private static long endOf(Bucket bucket, long width, RecordingSpan recording) {
        long end = bucket.startEpochMs() + width;
        long clipped = Math.min(end, recording.endEpochMs());
        return clipped > bucket.startEpochMs() ? clipped : end;
    }

    /**
     * One line the whole recording fits on, so steady load, a ramp, a sawtooth and a single spike are
     * told apart without reading any numbers. Scaled to the busiest bucket, so it describes
     * distribution rather than magnitude.
     */
    private static String shape(List<Bucket> buckets) {
        if (buckets.isEmpty()) {
            return "";
        }

        long peak = buckets.stream().mapToLong(Bucket::value).max().orElse(0);
        if (peak == 0) {
            return String.valueOf(SHAPE_RAMP[0]).repeat(Math.min(SHAPE_CELLS, buckets.size()));
        }

        int cells = Math.min(SHAPE_CELLS, buckets.size());
        StringBuilder line = new StringBuilder(cells);
        for (int cell = 0; cell < cells; cell++) {
            int from = cell * buckets.size() / cells;
            int to = Math.max((cell + 1) * buckets.size() / cells, from + 1);
            long max = 0;
            for (int i = from; i < to && i < buckets.size(); i++) {
                max = Math.max(max, buckets.get(i).value());
            }
            int level = (int) (max * (SHAPE_RAMP.length - 1) / peak);
            line.append(SHAPE_RAMP[level]);
        }
        return line.toString();
    }

    private static long requireBound(Long value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    /**
     * The export of one window, with the weighting the timeline was drawn with - passed even when it
     * is counting, since the export's own default for an allocation or blocking type is to weigh.
     */
    private McpNextTool.Call export(Type type, boolean weighted, Window window) {
        return NextCalls.to(EXPORT_TOOL)
                .with(PROFILE_ID, profileId())
                .with(EVENT_TYPE, type.code())
                .with(START_INPUT, window.startEpochMs())
                .with(END_INPUT, window.endEpochMs())
                .with(USE_WEIGHT, weighted);
    }

    /**
     * The zoom into one window, at buckets wide enough to stay under the cap: the default width for
     * a one-second window, wider only for a window that would need more.
     */
    private McpNextTool.Call zoom(Type type, Window window) {
        long length = window.endEpochMs() - window.startEpochMs();
        long width = Math.max(DEFAULT_ZOOM_BUCKET_MS, (length + MAX_ZOOM_BUCKETS - 1) / MAX_ZOOM_BUCKETS);
        McpNextTool.Call call = NextCalls.to(ZOOM_TOOL)
                .with(PROFILE_ID, profileId())
                .with(EVENT_TYPE, type.code())
                .with(START_INPUT, window.startEpochMs())
                .with(END_INPUT, window.endEpochMs());
        if (width > DEFAULT_ZOOM_BUCKET_MS) {
            call.with(BUCKET_MS, width);
        }
        return call;
    }

    private static Map<String, String> subsecondQuery(Type type) {
        Map<String, String> query = UiLinks.query();
        query.put(EVENT_TYPE_PARAM, type.code());
        query.put(GRAPH_MODE_PARAM, PRIMARY_GRAPH_MODE);
        return query;
    }

    private String profileId() {
        return profileManager.info().id();
    }

    /**
     * The parts every answer of one call shares, so the status answers and the ranked one are the same
     * record told apart only by what they found.
     */
    private final class Answer {

        private final Type type;
        private final boolean weighted;
        private final String uiLink;

        private Answer(Type type, boolean weighted, String uiLink) {
            this.type = type;
            this.weighted = weighted;
            this.uiLink = uiLink;
        }

        /** No timeline to rank at all; the whole-recording graph is what the profile can still give. */
        McpToolResult noTimeseries(String reason) {
            McpFollowUp followUp = NextSteps.builder(advertised)
                    .next(NextCalls.to(EXPORT_TOOL)
                            .with(PROFILE_ID, profileId()).with(EVENT_TYPE, type.code())
                            .with(USE_WEIGHT, weighted).why(EXPORT_WHOLE_WHY))
                    .followUp();
            return empty(TimelineStatus.NO_TIMESERIES, reason, null, followUp);
        }

        /** Nothing of this event type in the span asked about, and where to look instead. */
        McpToolResult notRecorded(EpochWindow window, McpNextTool instead) {
            McpFollowUp followUp = NextSteps.builder(advertised).next(instead).followUp();
            return empty(TimelineStatus.NOT_RECORDED, NOTHING_RECORDED.formatted(type.code()), window, followUp);
        }

        McpToolResult ranked(
                EpochWindow window, List<Bucket> buckets, long width, List<Window> windows, McpFollowUp followUp) {
            long withSamples = buckets.stream().filter(bucket -> bucket.value() > 0).count();
            return McpToolResult.of(new Timeline(TimelineStatus.OK, null, profileId(), type.code(), weighted,
                    window, total(buckets), buckets.size(), width, shape(buckets), windows,
                    withSamples - windows.size(), followUp, uiLink));
        }

        private McpToolResult empty(TimelineStatus status, String reason, EpochWindow window, McpFollowUp followUp) {
            return McpToolResult.of(new Timeline(status, reason, profileId(), type.code(), weighted,
                    window, 0, 0, 0, "", List.of(), 0, followUp, uiLink));
        }
    }

    private record Bucket(long startEpochMs, long value) {
    }

    /**
     * A window ready to hand to the next tool: {@code startEpochMs} and {@code endEpochMs} are the
     * arguments {@code flamegraph_export} and {@code timeline_zoom} take, on the base they take them.
     */
    record Window(
            @McpDescription("Start of the window, as UTC epoch milliseconds")
            long startEpochMs,
            @McpDescription("End of the window, as UTC epoch milliseconds")
            long endEpochMs,
            long value,
            @McpDescription("This window's share of the total, in percent")
            double percentOfTotal) {
    }

    /**
     * When the samples of one event type landed.
     *
     * @param window        the span the buckets cover: the recording for hotWindows, the window asked
     *                      about for zoom; null when the profile carries no recording span
     * @param bucketWidthMs how wide each bucket is; 0 when there are none
     */
    record Timeline(
            TimelineStatus status,
            @McpNullable
            @McpDescription("Why there is nothing to rank; null when status is OK")
            String reason,
            String profileId,
            String eventType,
            boolean weighted,
            @McpNullable
            @McpDescription("The span the buckets cover, as UTC epoch milliseconds")
            EpochWindow window,
            long total,
            int buckets,
            long bucketWidthMs,
            @McpDescription("One line of the distribution over time, scaled to the busiest bucket")
            String shape,
            List<Window> hottestWindows,
            @McpDescription("Windows with samples left out of hottestWindows by its cap")
            long unrankedWindows,
            McpFollowUp followUp,
            @McpDescription("The page in the Microscope UI that draws this timeline, for the user")
            String uiLink) {
    }
}
