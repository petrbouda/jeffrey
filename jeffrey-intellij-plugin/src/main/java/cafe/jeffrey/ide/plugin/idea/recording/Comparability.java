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

package cafe.jeffrey.ide.plugin.idea.recording;

/**
 * Whether two recordings can be subtracted from one another, said before anyone reads a delta.
 *
 * <p>Any two recordings can be compared, and the result always looks like a finding. Whether it is
 * one depends on facts the deltas do not show — that both runs did comparable work, for a comparable
 * length of time, with the same profiler settings — and nothing inside a JFR file proves any of
 * them. The {@code compare-jfr} skill makes {@code compare_list} its first call for exactly this
 * reason, and treats "these two are not comparable" as a reportable result rather than a warm-up.
 *
 * <p>This is the panel's cheap half of that check. It reads only the figures the panel already has,
 * so it costs no extra call and can be drawn beside the numbers a reader is about to compare. It
 * does not replace {@code compare_list}: what it can see is the recording window, the event type
 * count and the kind, which is enough to catch the two mistakes that make a comparison worthless
 * before it is opened, and no more than that.
 *
 * <p>Pure and framework-free, which is what lets the wording be pinned by a test rather than by a
 * reading of the panel.
 */
public record Comparability(
        Level level,
        boolean windowsDiffer,
        boolean eventTypesDiffer,
        String headline,
        String detail) {

    /**
     * How far apart two windows may be before the panel says so — a quarter.
     *
     * <p>Recordings of the same workload rarely land on the same second, so a tolerance of zero
     * would caution about every honest pair and teach a reader to ignore the callout. A quarter is
     * wide enough to pass an ordinary pair of runs and narrow enough to catch the case that actually
     * misleads: a baseline of a few minutes against a primary of twenty.
     */
    public static final double WINDOW_TOLERANCE = 0.25;

    private static final String COMPARABLE_HEADLINE = "Comparable";

    private static final String COMPARABLE_DETAIL =
            "The windows are within a quarter of each other and both sides recorded the same number "
            + "of event types. Whether the two runs did the same work is yours to know: the "
            + "recordings cannot say.";

    private static final String WINDOWS_HEADLINE = "The recording windows differ";

    private static final String EVENT_TYPES_HEADLINE = "The recordings carry different event types";

    private static final String BOTH_HEADLINE = "These two are not a like-for-like pair";

    private static final String WINDOWS_DETAIL_PREFIX = "The primary covers ";

    private static final String WINDOWS_DETAIL_MIDDLE = " and the baseline ";

    private static final String WINDOWS_DETAIL_SUFFIX =
            ". Microscope scales the baseline onto the primary's length, which is right for a steady "
            + "workload and wrong for a fixed-size benchmark: on a benchmark read the share column "
            + "rather than the delta.";

    private static final String EVENT_TYPES_DETAIL_PREFIX = "One side recorded ";

    private static final String EVENT_TYPES_DETAIL_MIDDLE = " event types and the other ";

    private static final String EVENT_TYPES_DETAIL_SUFFIX =
            ". A type present on one side only is a difference between the two profiler "
            + "configurations, not a change in the application.";

    private static final String INCOMPARABLE_HEADLINE = "These two cannot be compared";

    private static final String INCOMPARABLE_HEAP_DETAIL =
            "A heap dump and a recording answer different questions and share no event types. "
            + "Microscope compares two dumps on the heap dump's own diff page.";

    private static final String INCOMPARABLE_FIGURES_DETAIL =
            "Microscope has no figures for one of these profiles, so there is nothing to weigh the "
            + "other against.";

    private static final String DETAIL_SEPARATOR = " ";

    public enum Level {
        /** Nothing in the figures argues against the comparison. */
        COMPARABLE,
        /** It can be read, but something about the pair will mislead a reader who does not know. */
        CAUTION,
        /** There is nothing to compare. */
        INCOMPARABLE
    }

    /**
     * The verdict on a pair, from what the panel already knows about each side.
     *
     * <p>Direction does not matter to any of these checks — a fourfold difference is one whichever
     * way round it is read — so the primary and the baseline are interchangeable here even though
     * they are not anywhere else.
     */
    public static Comparability of(RecordingState primary, RecordingState baseline) {
        if (primary.isHeapDumpFile() || baseline.isHeapDumpFile()) {
            return incomparable(INCOMPARABLE_HEAP_DETAIL);
        }

        RecordingState.RecordingFigures primaryFigures = figuresOf(primary);
        RecordingState.RecordingFigures baselineFigures = figuresOf(baseline);
        if (primaryFigures == null || baselineFigures == null) {
            return incomparable(INCOMPARABLE_FIGURES_DETAIL);
        }

        boolean windowsDiffer = !withinTolerance(
                primaryFigures.durationInMillis(), baselineFigures.durationInMillis());
        boolean eventTypesDiffer =
                primaryFigures.eventTypeCount() != baselineFigures.eventTypeCount();

        if (!windowsDiffer && !eventTypesDiffer) {
            return new Comparability(Level.COMPARABLE, false, false, COMPARABLE_HEADLINE, COMPARABLE_DETAIL);
        }
        return new Comparability(
                Level.CAUTION,
                windowsDiffer,
                eventTypesDiffer,
                headlineFor(windowsDiffer, eventTypesDiffer),
                detailFor(windowsDiffer, eventTypesDiffer, primaryFigures, baselineFigures));
    }

    /**
     * A verdict with nothing to weigh. Both differences read false: neither is known to hold, and
     * saying otherwise would colour a figure this pair has no opinion about.
     */
    private static Comparability incomparable(String detail) {
        return new Comparability(Level.INCOMPARABLE, false, false, INCOMPARABLE_HEADLINE, detail);
    }

    /** Whether the panel should draw this as a callout rather than a line of prose. */
    public boolean isCautioned() {
        return level != Level.COMPARABLE;
    }

    private static String headlineFor(boolean windowsDiffer, boolean eventTypesDiffer) {
        if (windowsDiffer && eventTypesDiffer) {
            return BOTH_HEADLINE;
        }
        if (windowsDiffer) {
            return WINDOWS_HEADLINE;
        }
        return EVENT_TYPES_HEADLINE;
    }

    private static String detailFor(
            boolean windowsDiffer,
            boolean eventTypesDiffer,
            RecordingState.RecordingFigures primary,
            RecordingState.RecordingFigures baseline) {

        StringBuilder detail = new StringBuilder(256);
        if (windowsDiffer) {
            detail.append(WINDOWS_DETAIL_PREFIX)
                    .append(Formats.duration(primary.durationInMillis()))
                    .append(WINDOWS_DETAIL_MIDDLE)
                    .append(Formats.duration(baseline.durationInMillis()))
                    .append(WINDOWS_DETAIL_SUFFIX);
        }
        if (eventTypesDiffer) {
            if (windowsDiffer) {
                detail.append(DETAIL_SEPARATOR);
            }
            detail.append(EVENT_TYPES_DETAIL_PREFIX)
                    .append(primary.eventTypeCount())
                    .append(EVENT_TYPES_DETAIL_MIDDLE)
                    .append(baseline.eventTypeCount())
                    .append(EVENT_TYPES_DETAIL_SUFFIX);
        }
        return detail.toString();
    }

    /**
     * Whether two windows are close enough to read as the same length.
     *
     * <p>A window Microscope did not report is not evidence of a mismatch, so two unreported windows
     * pass; one reported against one missing does not, because then the pair genuinely cannot be
     * weighed on length.
     */
    private static boolean withinTolerance(long primaryMillis, long baselineMillis) {
        if (primaryMillis <= 0 && baselineMillis <= 0) {
            return true;
        }
        if (primaryMillis <= 0 || baselineMillis <= 0) {
            return false;
        }
        double longer = Math.max(primaryMillis, baselineMillis);
        double shorter = Math.min(primaryMillis, baselineMillis);
        return longer / shorter <= 1 + WINDOW_TOLERANCE;
    }

    private static RecordingState.RecordingFigures figuresOf(RecordingState state) {
        RecordingState.ProfileSummary summary = state.summary();
        return summary == null ? null : summary.recording();
    }
}
