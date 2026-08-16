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

package cafe.jeffrey.profile.ai.trace;

import cafe.jeffrey.profile.manager.model.trace.TimelineDensityBucket;
import cafe.jeffrey.profile.manager.model.trace.TimelineSpan;
import cafe.jeffrey.profile.manager.model.trace.TimelineStatePeriod;
import cafe.jeffrey.profile.manager.model.trace.TimelineTrack;
import cafe.jeffrey.profile.manager.model.trace.TimelineWindow;
import cafe.jeffrey.profile.manager.model.trace.TracePause;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders one unified-timeline window — every thread, span, pause and wait between two instants —
 * as Markdown for a model to read.
 * <p>
 * A sibling of {@link TraceAiMarkdownBuilder} and {@link TraceOperationAiMarkdownBuilder} with its
 * own warnings: a window is neither one request nor a population, it is a cross-section of the
 * whole JVM, and the mistake a reader makes here is attributing what one thread suffered to another
 * — or summing global pauses once per thread.
 */
public final class TimelineWindowAiMarkdownBuilder {

    private static final String AI_PREAMBLE = """
            # How to read this timeline window

            This document describes **one slice of time across the whole JVM** — every
            thread that ran spans between two instants, the stop-the-world pauses that
            crossed the slice, and what each thread was waiting on — exported by Jeffrey
            (a JVM performance analyst) for AI-assisted interpretation.

            ## Sections

            1. **Header (YAML-ish)** — the window's bounds and what it holds.
            2. **Stop-the-world pauses** — GC pauses and safepoints inside the window.
            3. **Threads** — per thread: the spans that ran and the waits recorded.

            ## Rules that prevent wrong conclusions

            - **Pauses are global.** A GC pause stops *every* thread at once. It is listed
              once, but its cost applies to each thread that was doing work when it fired.
              Never sum a pause once per thread — that multiplies one pause by the thread
              count.
            - **Waits are thread-scoped.** A `PARKED` or `SOCKET_IO` period belongs to
              exactly the thread it is listed under; it says nothing about any other thread.
            - **A window is not a population.** What happened here may be typical or may be
              the one bad moment someone zoomed into. Do not generalise beyond the window;
              name what a wider view would need to confirm.
            - Span times are wall-clock durations, not CPU time.

            """;

    private static final String HEADING_ANALYSIS = "## How to analyze this window";
    private static final String HEADING_PAUSES = "## Stop-the-world pauses";
    private static final String HEADING_THREADS = "## Threads";

    private static final String ANALYSIS_INSTRUCTION = """
            Read vertically first: find the instants where a pause or a burst of waits
            lines up across several threads — that is the signature of a shared cause, and
            it is the one thing this cross-section can show that a per-request view cannot.

            Then read each busy thread horizontally: long spans with long waits under them
            are threads losing time to contention or I/O; long spans with nothing under
            them were computing.

            Conclude with which single trace or which narrower window would be worth
            exporting next, rather than concluding causes this slice alone cannot prove.
            """;

    private static final String BULLET_PREFIX = "- ";
    private static final String DASH_SEPARATOR = " — ";
    private static final String NO_PAUSES_NOTE = "(no stop-the-world pauses crossed this window)";
    private static final String NO_TRACKS_NOTE = "(no spans ran in this window)";
    /** Spans listed per thread; the busiest carry the story and the tail of slivers does not. */
    private static final int SPANS_PER_TRACK = 8;
    /** Threads rendered, busiest first; a 200-thread dump would drown the signal in idle pools. */
    private static final int TRACKS_LIMIT = 20;
    private static final long NANOS_PER_MICRO = 1_000L;
    private static final long MICROS_PER_MILLI = 1_000L;

    private final TimelineWindow window;

    public TimelineWindowAiMarkdownBuilder(TimelineWindow window) {
        this.window = window;
    }

    public String build() {
        StringBuilder out = new StringBuilder(8192);
        out.append(AI_PREAMBLE);
        renderHeader(out);
        out.append('\n');
        renderAnalysisInstruction(out);
        renderPauses(out);
        out.append('\n');
        renderThreads(out);
        return out.toString();
    }

    private void renderHeader(StringBuilder out) {
        long widthNanos = (window.toEpochMicros() - window.fromEpochMicros()) * NANOS_PER_MICRO;
        out.append("window_start_utc: ").append(utc(window.fromEpochMicros())).append('\n');
        out.append("window_end_utc: ").append(utc(window.toEpochMicros())).append('\n');
        out.append("window_length: ").append(TraceAiFormat.duration(widthNanos)).append('\n');
        out.append("threads_with_spans: ").append(window.tracks().size()).append('\n');
        out.append("pauses: ").append(window.pauses().size()).append('\n');
        if (window.truncated()) {
            out.append("note: the window held more spans than the export cap; the per-thread span ")
                    .append("lists below are incomplete and say so").append('\n');
        }
        if (window.statesTruncated()) {
            out.append("note: the window held more waits than the export cap; wait totals are ")
                    .append("lower bounds").append('\n');
        }
    }

    private void renderAnalysisInstruction(StringBuilder out) {
        out.append(HEADING_ANALYSIS).append('\n').append('\n');
        out.append(ANALYSIS_INSTRUCTION).append('\n');
    }

    private void renderPauses(StringBuilder out) {
        out.append(HEADING_PAUSES).append('\n').append('\n');
        if (window.pauses().isEmpty()) {
            out.append(NO_PAUSES_NOTE).append('\n');
            return;
        }
        for (TracePause pause : window.pauses()) {
            out.append(BULLET_PREFIX)
                    .append(pause.category()).append(DASH_SEPARATOR).append(pause.label())
                    .append(DASH_SEPARATOR).append(TraceAiFormat.duration(pause.durationNanos()))
                    .append(" at +").append(offset(pause.startEpochMicros()))
                    .append(" into the window");
            out.append('\n');
        }
    }

    /**
     * Busiest threads first, by the span time they carried: the export exists to explain where the
     * window's time went, and an idle pool thread explains none of it.
     */
    private void renderThreads(StringBuilder out) {
        out.append(HEADING_THREADS).append('\n').append('\n');
        if (window.tracks().isEmpty()) {
            out.append(NO_TRACKS_NOTE).append('\n');
            return;
        }

        List<TimelineTrack> busiest = window.tracks().stream()
                .sorted(Comparator.comparingLong(TimelineWindowAiMarkdownBuilder::spanNanosOf)
                        .reversed())
                .limit(TRACKS_LIMIT)
                .toList();
        if (window.tracks().size() > busiest.size()) {
            out.append("(showing the ").append(busiest.size()).append(" busiest of ")
                    .append(window.tracks().size()).append(" threads)").append('\n').append('\n');
        }

        for (TimelineTrack track : busiest) {
            renderTrack(out, track);
            out.append('\n');
        }
    }

    /**
     * How busy a track was, for the ranking. In density mode there are no spans to sum, so the
     * bucket counts stand in — a coarser measure, but the ranking only has to order, not measure.
     */
    private static long spanNanosOf(TimelineTrack track) {
        long spanNanos = track.spans().stream().mapToLong(TimelineSpan::durationNanos).sum();
        if (spanNanos > 0) {
            return spanNanos;
        }
        return track.density().stream().mapToLong(TimelineDensityBucket::count).sum();
    }

    private void renderTrack(StringBuilder out, TimelineTrack track) {
        out.append("### ").append(track.threadName() == null ? "unknown thread" : track.threadName());
        if (track.isVirtual()) {
            out.append(" (virtual)");
        }
        out.append('\n').append('\n');

        renderTrackSpans(out, track);
        renderTrackWaits(out, track);
    }

    private void renderTrackSpans(StringBuilder out, TimelineTrack track) {
        if (track.spans().isEmpty()) {
            // Density mode: the window was too busy to list spans at all, and saying nothing here
            // would read as an idle thread.
            if (!track.density().isEmpty()) {
                long total = track.density().stream().mapToLong(bucket -> bucket.count()).sum();
                out.append(TraceAiFormat.count(total, "span")).append(" ran on this thread — too many ")
                        .append("to list; export a narrower window for detail").append('\n');
            }
            return;
        }

        List<TimelineSpan> longest = track.spans().stream()
                .sorted(Comparator.comparingLong(TimelineSpan::durationNanos).reversed())
                .limit(SPANS_PER_TRACK)
                .toList();
        out.append(TraceAiFormat.count(track.spans().size(), "span")).append(" in the window");
        if (track.spans().size() > longest.size()) {
            out.append("; the ").append(longest.size()).append(" longest:");
        } else {
            out.append(':');
        }
        out.append('\n');
        for (TimelineSpan span : longest) {
            out.append(BULLET_PREFIX).append(span.name())
                    .append(DASH_SEPARATOR).append(TraceAiFormat.duration(span.durationNanos()))
                    .append(", ").append(span.kind())
                    .append(", trace ").append(span.traceId());
            if ("ERROR".equals(span.status())) {
                out.append(", ERROR");
            }
            out.append('\n');
        }
    }

    /** Waits summed per category — the count matters as much as the total (see the trace export). */
    private void renderTrackWaits(StringBuilder out, TimelineTrack track) {
        if (track.states().isEmpty()) {
            return;
        }
        Map<String, long[]> byCategory = new LinkedHashMap<>();
        for (TimelineStatePeriod state : track.states()) {
            long[] entry = byCategory.computeIfAbsent(state.category(), _ -> new long[2]);
            entry[0] += state.durationNanos();
            entry[1]++;
        }
        out.append("waited on: ");
        boolean first = true;
        for (Map.Entry<String, long[]> entry : byCategory.entrySet()) {
            if (!first) {
                out.append("; ");
            }
            first = false;
            out.append(entry.getKey()).append(' ')
                    .append(TraceAiFormat.duration(entry.getValue()[0]))
                    .append(" across ").append(TraceAiFormat.count(entry.getValue()[1], "event"));
        }
        out.append('\n');
    }

    private String offset(long epochMicros) {
        return TraceAiFormat.duration((epochMicros - window.fromEpochMicros()) * NANOS_PER_MICRO);
    }

    /** Formatting recorded data, not reading the clock — {@code java.time.Clock} is not involved. */
    private static String utc(long epochMicros) {
        return Instant.ofEpochMilli(Math.floorDiv(epochMicros, MICROS_PER_MILLI)).toString();
    }
}
