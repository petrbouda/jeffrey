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

import cafe.jeffrey.profile.manager.model.trace.TraceSpanRow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which rows the span tree renders, chosen by what a reader needs rather than by position.
 * <p>
 * The tree has a span budget, and a budget spent in tree order is spent on whatever comes first.
 * In a trace whose recording captured every file read, that is hundreds of promoted one-microsecond
 * leaves ahead of the recorded spans that actually explain the time -- the export then lists four
 * hundred lines named "File read" and drops the phases behind them. The plan spends the budget in
 * three tiers instead:
 * <ol>
 *   <li><b>Recorded spans</b> -- and any promoted span that has children, since a traced method with
 *       recorded work inside it is structure, not a wait -- are listed first, in tree order.</li>
 *   <li><b>Promoted leaves long enough to matter</b> take a line of their own while the budget
 *       lasts. Long enough is the floor: at least {@link #LEAF_FLOOR_NANOS}, or a thousandth of the
 *       trace, whichever is larger.</li>
 *   <li><b>Every other promoted leaf</b> is folded into one line per parent and name, placed where
 *       the first of them stood. Nothing is lost to the accounting: the I/O section counts each
 *       operation individually regardless.</li>
 * </ol>
 * A span past the budget in tier one is omitted, and the plan says how many; a leaf whose parent
 * was omitted is omitted with it, since a fold has nothing to hang under.
 */
final class TraceTreePlan {

    /** The shortest promoted leaf that earns its own line, before the trace-relative floor. */
    static final long LEAF_FLOOR_NANOS = 1_000_000L;

    /** The trace-relative floor: a leaf under this fraction of the trace is folded. */
    private static final long LEAF_FLOOR_SHARE_DENOMINATOR = 1_000L;

    /** The origin the derivation stamps on a read the class loader asked for. */
    private static final String CLASS_LOADING_ORIGIN = "CLASS_LOADING";

    /** One line of the tree: a span on its own, or a fold of promoted leaves. */
    sealed interface Row permits SpanLine, FoldLine {
    }

    /** A span rendered individually. */
    record SpanLine(TraceSpanRow span) implements Row {
    }

    /**
     * Promoted leaves under one parent sharing one name, rendered as a single line.
     *
     * @param name         the leaves' shared name
     * @param kind         the kind of the first leaf, which is the kind of all of them
     * @param depth        where the line sits, which is where the leaves sat
     * @param count        how many leaves the fold holds
     * @param totalNanos   their durations summed
     * @param maxNanos     the longest of them
     * @param classLoading whether every read in the fold was the class loader's
     */
    record FoldLine(
            String name, String kind, int depth, int count, long totalNanos, long maxNanos,
            boolean classLoading) implements Row {
    }

    /** What makes two leaves fold together: the same parent and the same name. */
    private record FoldKey(String parentSpanId, String name) {
    }

    /** A fold while it is still being built, before it is frozen into a {@link FoldLine}. */
    private static final class Fold {
        private final String name;
        private final String kind;
        private final int depth;
        private int count;
        private long totalNanos;
        private long maxNanos;
        private boolean classLoading = true;

        private Fold(TraceSpanRow first) {
            this.name = first.name();
            this.kind = first.kind();
            this.depth = first.depth();
        }

        private void add(TraceSpanRow span) {
            count++;
            totalNanos += span.durationNanos();
            maxNanos = Math.max(maxNanos, span.durationNanos());
            classLoading &= CLASS_LOADING_ORIGIN.equals(span.ioOrigin());
        }

        private FoldLine freeze() {
            return new FoldLine(name, kind, depth, count, totalNanos, maxNanos, classLoading);
        }
    }

    private final List<Row> rows;
    private final int folded;
    private final int omitted;

    private TraceTreePlan(List<Row> rows, int folded, int omitted) {
        this.rows = rows;
        this.folded = folded;
        this.omitted = omitted;
    }

    /**
     * Plans the tree for {@code spans}, which arrive pre-ordered exactly as the waterfall draws
     * them.
     *
     * @param spans      every span of the trace, depth-first, siblings by start
     * @param traceNanos the trace's own duration, which sets the trace-relative floor
     * @param maxLines   how many spans may take a line of their own
     */
    static TraceTreePlan of(List<TraceSpanRow> spans, long traceNanos, int maxLines) {
        long floorNanos = Math.max(LEAF_FLOOR_NANOS, traceNanos / LEAF_FLOOR_SHARE_DENOMINATOR);

        Set<String> parents = new HashSet<>();
        for (TraceSpanRow span : spans) {
            if (span.parentSpanId() != null) {
                parents.add(span.parentSpanId());
            }
        }

        // Tier one: recorded spans and promoted spans that carry children.
        Set<String> rendered = new HashSet<>();
        for (TraceSpanRow span : spans) {
            if (!isPromotedLeaf(span, parents) && rendered.size() < maxLines) {
                rendered.add(span.spanId());
            }
        }

        // Tier two: promoted leaves above the floor, while the budget lasts.
        for (TraceSpanRow span : spans) {
            if (isPromotedLeaf(span, parents)
                    && span.durationNanos() >= floorNanos
                    && rendered.size() < maxLines) {
                rendered.add(span.spanId());
            }
        }

        // Tier three: everything else promoted folds under its parent, if the parent is there.
        Map<FoldKey, Fold> folds = new LinkedHashMap<>();
        Map<String, FoldKey> foldOf = new HashMap<>();
        int omitted = 0;
        for (TraceSpanRow span : spans) {
            if (rendered.contains(span.spanId())) {
                continue;
            }
            boolean parentRendered = span.parentSpanId() != null && rendered.contains(span.parentSpanId());
            if (!isPromotedLeaf(span, parents) || !parentRendered) {
                omitted++;
                continue;
            }
            FoldKey key = new FoldKey(span.parentSpanId(), span.name());
            folds.computeIfAbsent(key, k -> new Fold(span)).add(span);
            foldOf.put(span.spanId(), key);
        }

        // Lines in tree order: a fold takes the place of the first leaf it swallowed.
        List<Row> rows = new ArrayList<>(rendered.size() + folds.size());
        Set<FoldKey> emitted = new HashSet<>();
        int folded = 0;
        for (TraceSpanRow span : spans) {
            if (rendered.contains(span.spanId())) {
                rows.add(new SpanLine(span));
                continue;
            }
            FoldKey key = foldOf.get(span.spanId());
            if (key != null && emitted.add(key)) {
                FoldLine line = folds.get(key).freeze();
                folded += line.count();
                rows.add(line);
            }
        }
        return new TraceTreePlan(List.copyOf(rows), folded, omitted);
    }

    /** A promoted span with nothing under it -- a wait or an I/O operation, never a method. */
    private static boolean isPromotedLeaf(TraceSpanRow span, Set<String> parents) {
        return span.synthesized() && !parents.contains(span.spanId());
    }

    /** The lines to render, in tree order. */
    List<Row> rows() {
        return rows;
    }

    /** How many promoted leaves were folded into {@link FoldLine}s. */
    int folded() {
        return folded;
    }

    /** How many fold lines the tree carries. */
    int foldLines() {
        int count = 0;
        for (Row row : rows) {
            if (row instanceof FoldLine) {
                count++;
            }
        }
        return count;
    }

    /** How many spans appear nowhere in the tree: past the budget, or under a span that was. */
    int omitted() {
        return omitted;
    }
}
