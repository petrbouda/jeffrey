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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.core.mcp.LinkedOutput;
import cafe.jeffrey.microscope.core.mcp.UiLinks;
import cafe.jeffrey.profile.common.config.GraphParameters;
import cafe.jeffrey.profile.feature.FeatureType;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.TimeseriesManager;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.timeseries.SingleSerie;
import tools.jackson.databind.JsonNode;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * When something happened, rather than where.
 * <p>
 * Every other tool that takes a window — {@code flamegraph_export}, {@code compare_flamegraph}, the
 * trace exports — accepts {@code startMs} and {@code endMs} and offers no way to choose them. A
 * flamegraph of a whole recording flattens a thirty-second spike into a five-minute average and the
 * spike stops being visible. This family picks the window; the flamegraph then explains it.
 * <p>
 * <strong>The reduction is the product, not the series.</strong> The managers behind this return
 * {@link SingleSerie}, which is chart geometry — {@code [[timestamp, value], …]}, three hundred
 * points for a five-minute recording at one-second resolution and thirty thousand at ten
 * milliseconds. Handing that to a model spends a large part of the output budget on a curve it
 * cannot act on, which is why the dashboards drop their series entirely. What is useful is the
 * <em>shape</em>: which windows carry the mass, ranked, each with the bounds to pass straight to the
 * next tool, plus a coarse profile line so a steady load, a sawtooth, a ramp and a single spike are
 * distinguishable at a glance. A raw series must never leave this class.
 */
public class TimelineMcpTools {

    private static final String TIMESERIES_VIEW = "flamegraphs/primary";
    private static final String SUBSECOND_VIEW = "subsecond/primary";
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

    private static final String NO_TIMESERIES_DATA =
            "This profile carries no time-resolved data. pprof and OTLP imports are aggregated and hold "
                    + "no per-sample timestamps, so there is no timeline to draw - every sample is "
                    + "attributed to the whole recording at once.";

    private static final String NOTHING_RECORDED =
            "No samples of '%s' were recorded, so there is no timeline for it. flamegraph_list names "
                    + "the event types this profile actually captured.";

    private static final String STEP_EXPORT =
            "flamegraph_export with the startMs and endMs of a window above graphs only what happened "
                    + "inside it - the frames a whole-recording export averages away.";
    private static final String STEP_ZOOM =
            "timeline_zoom resolves below one second inside a window, which is what a startup or the "
                    + "inside of a spike needs.";
    private static final String STEP_WIDER =
            "timeline_hotWindows covers the whole recording, if this window turns out not to be the "
                    + "interesting one.";

    private final ProfileManager profileManager;

    public TimelineMcpTools(ProfileManager profileManager) {
        this.profileManager = profileManager;
    }

    @Tool(description = "When the samples of one event type actually landed: the recording split into "
            + "buckets, the busiest windows ranked, and a coarse shape line. Use it before exporting a "
            + "flamegraph of anything bursty - a whole-recording graph averages a spike away, and the "
            + "startMs and endMs of a window here are what make the export show it. Answers 'when did "
            + "the allocation happen', 'was this load steady or a burst', 'which minute was the bad one'.")
    public String hotWindows(
            @ToolParam(description = "Event type to profile over time, e.g. 'jdk.ExecutionSample' or "
                    + "'jdk.ObjectAllocationSample'. Use flamegraph_list to see what this profile recorded.")
            String eventType,
            @ToolParam(description = "Weigh buckets by the event's weight (bytes allocated, nanoseconds "
                    + "blocked) instead of by sample count")
            Boolean useWeight,
            @ToolParam(description = "How many windows to rank (default 5, maximum 25)")
            Integer top) {

        if (timelineUnavailable()) {
            return NO_TIMESERIES_DATA;
        }

        Type type = FlamegraphMcpTools.requireEventType(eventType);
        SingleSerie serie = primarySerie(type, Boolean.TRUE.equals(useWeight));
        if (serie == null || serie.data().isEmpty()) {
            return NOTHING_RECORDED.formatted(type.code());
        }

        List<Bucket> buckets = buckets(serie);
        return LinkedOutput.json(new Timeline(
                type.code(),
                Boolean.TRUE.equals(useWeight),
                total(buckets),
                buckets.size(),
                bucketWidthMs(buckets),
                shape(buckets),
                topWindows(buckets, boundedTop(top)),
                NextSteps.builder().add(STEP_EXPORT).add(STEP_ZOOM).build(),
                UiLinks.view(profileId(), TIMESERIES_VIEW)));
    }

    @Tool(description = "The same shape at sub-second resolution inside one window: the only view that "
            + "resolves below a second. Use it for a startup - the first seconds of a recording, where "
            + "one-second buckets hide everything - or to see the inside of a spike timeline_hotWindows "
            + "found. Narrow the window rather than widening the buckets; the bucket count is capped.")
    public String zoom(
            @ToolParam(description = "Event type to profile, e.g. 'jdk.ExecutionSample'")
            String eventType,
            @ToolParam(description = "Start of the window, in milliseconds from the beginning of the "
                    + "recording")
            Long startMs,
            @ToolParam(description = "End of the window, in milliseconds from the beginning of the recording")
            Long endMs,
            @ToolParam(description = "Bucket width in milliseconds (default 20). Below one second is the "
                    + "point of this tool; a window that would need more than 200 buckets is refused "
                    + "rather than silently coarsened.")
            Integer bucketMs) {

        if (timelineUnavailable()) {
            return NO_TIMESERIES_DATA;
        }

        Type type = FlamegraphMcpTools.requireEventType(eventType);
        long from = requireBound(startMs, "startMs");
        long to = requireBound(endMs, "endMs");
        if (to <= from) {
            throw new IllegalArgumentException("endMs must be greater than startMs: " + from + " >= " + to);
        }

        int width = bucketMs == null ? DEFAULT_ZOOM_BUCKET_MS : bucketMs;
        if (width < MIN_ZOOM_BUCKET_MS) {
            throw new IllegalArgumentException("bucketMs must be at least " + MIN_ZOOM_BUCKET_MS);
        }
        long wanted = (to - from) / width;
        if (wanted > MAX_ZOOM_BUCKETS) {
            throw new IllegalArgumentException(
                    "A " + (to - from) + " ms window at " + width + " ms buckets needs " + wanted
                            + " buckets, more than the " + MAX_ZOOM_BUCKETS + " cap. Narrow the window "
                            + "or widen the buckets.");
        }

        List<Bucket> window = subSecondBuckets(type, from, to, width);
        if (window.isEmpty()) {
            return NOTHING_RECORDED.formatted(type.code());
        }

        return LinkedOutput.json(new Timeline(
                type.code(),
                false,
                total(window),
                window.size(),
                width,
                shape(window),
                topWindows(window, DEFAULT_TOP_WINDOWS),
                NextSteps.builder().add(STEP_EXPORT).add(STEP_WIDER).build(),
                UiLinks.view(profileId(), SUBSECOND_VIEW, subsecondQuery(type))));
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
                .withTimeRange(FlamegraphMcpTools.timeRange(profileManager.info(), null, null))
                .withUseWeight(useWeight)
                .build();

        List<SingleSerie> series =
                profileManager.timeseriesManager()
                        .timeseries(new TimeseriesManager.Generate(type, parameters, null))
                        .series();
        return series.isEmpty() ? null : series.getFirst();
    }

    /**
     * The series as {@code (startMs, value)} pairs. This is the only place the raw shape is read, and
     * nothing downstream of it returns one.
     * <p>
     * The series is keyed by <em>second</em> from the start of the recording - {@code
     * SecondValueTimeseriesBuilder} adds to {@code record.second()}, and the frontend multiplies by a
     * thousand before plotting. The conversion happens here, once, because every bound this class
     * hands out is fed to a tool that reads milliseconds: leaving it in seconds would produce windows
     * a thousand times too early and a flamegraph of the wrong instant.
     */
    private static List<Bucket> buckets(SingleSerie serie) {
        List<Bucket> buckets = new ArrayList<>(serie.data().size());
        for (List<Long> point : serie.data()) {
            if (point.size() >= 2 && point.get(0) != null && point.get(1) != null) {
                buckets.add(new Bucket(point.getFirst() * MILLIS_PER_SECOND, point.get(1)));
            }
        }
        return buckets;
    }

    /**
     * Genuine sub-second buckets, from the heatmap the subsecond view is drawn from rather than from
     * the one-second series - re-aggregating that could not invent detail it does not carry, which is
     * the whole reason this tool exists.
     * <p>
     * The heatmap is a matrix: one row per offset within a second, one cell per second. Flattening it
     * back to absolute milliseconds is what turns it into something rankable.
     */
    private List<Bucket> subSecondBuckets(Type type, long from, long to, int width) {
        JsonNode model = profileManager.subSecondManager()
                .generate(type, false, FlamegraphMcpTools.timeRange(profileManager.info(), from, to), width);

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
                    buckets.add(new Bucket(from + second * MILLIS_PER_SECOND + offsetMs, value));
                }
            }
        }
        buckets.sort(Comparator.comparingLong(Bucket::startMs));
        return buckets;
    }

    private static long total(List<Bucket> buckets) {
        return buckets.stream().mapToLong(Bucket::value).sum();
    }

    private static long bucketWidthMs(List<Bucket> buckets) {
        if (buckets.size() < 2) {
            return 0;
        }
        return buckets.get(1).startMs() - buckets.getFirst().startMs();
    }

    /**
     * The windows carrying the most, each with the bounds the next tool takes. Ranked by value, not by
     * time: the point is to find the one worth graphing.
     */
    private static List<Window> topWindows(List<Bucket> buckets, int top) {
        long width = Math.max(bucketWidthMs(buckets), 1);
        long total = Math.max(total(buckets), 1);
        return buckets.stream()
                .filter(bucket -> bucket.value() > 0)
                .sorted(Comparator.comparingLong(Bucket::value).reversed())
                .limit(top)
                .map(bucket -> new Window(
                        bucket.startMs(),
                        bucket.startMs() + width,
                        bucket.value(),
                        Math.round(bucket.value() * 1000.0 / total) / 10.0))
                .toList();
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

    private static int boundedTop(Integer top) {
        if (top == null) {
            return DEFAULT_TOP_WINDOWS;
        }
        return Math.clamp(top, 1, MAX_TOP_WINDOWS);
    }

    private static long requireBound(Long value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative: " + value);
        }
        return value;
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

    private record Bucket(long startMs, long value) {
    }

    /**
     * A window ready to hand to the next tool: {@code startMs} and {@code endMs} are the arguments
     * {@code flamegraph_export} takes, in the units it takes them.
     */
    private record Window(long startMs, long endMs, long value, double percentOfTotal) {
    }

    private record Timeline(
            String eventType,
            boolean weighted,
            long total,
            int buckets,
            long bucketWidthMs,
            String shape,
            List<Window> hottestWindows,
            List<String> nextSteps,
            String uiLink) {
    }
}
