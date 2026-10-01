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
package cafe.jeffrey.profile.trace.export;

import cafe.jeffrey.profile.manager.model.trace.TraceSpanRow;
import cafe.jeffrey.profile.manager.model.trace.TraceSpanRunRow;

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
 * was omitted is omitted with it, since a fold has nothing to hang under. The fold lines have the
 * same budget: past it, the remaining groups collapse into one closing {@link CollapsedFolds} line
 * rather than each taking a line of its own.
 * <p>
 * Runs the server already folded — a hundred or more identical leaves under one parent — arrive as
 * {@link TraceSpanRunRow}s rather than spans, and become fold lines as they are: their members were
 * never sent, so there is nothing to tier. They take the same budget and the same rule about an
 * omitted parent as the folds built here.
 */
final class TraceTreePlan {

    /** The shortest promoted leaf that earns its own line, before the trace-relative floor. */
    static final long LEAF_FLOOR_NANOS = 1_000_000L;

    /** The trace-relative floor: a leaf under this fraction of the trace is folded. */
    private static final long LEAF_FLOOR_SHARE_DENOMINATOR = 1_000L;

    /** The origin the derivation stamps on a read the class loader asked for. */
    private static final String CLASS_LOADING_ORIGIN = "CLASS_LOADING";

    /** One line of the tree: a span, a fold of promoted leaves, or the folds past the budget. */
    sealed interface Row permits SpanLine, FoldLine, CollapsedFolds {
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

    /**
     * The fold groups past the budget, counted on one line at the end of the tree rather than each
     * given a line of its own.
     *
     * @param groups how many fold groups the line stands for
     * @param count  how many promoted leaves those groups hold
     */
    record CollapsedFolds(int groups, int count) implements Row {
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
     * them, with the server's folded {@code runs} placed among them.
     *
     * @param spans      every span of the trace not folded into a run, depth-first, siblings by start
     * @param runs       the runs the server folded, each placed before {@code spans[position]}
     * @param traceNanos the trace's own duration, which sets the trace-relative floor
     * @param maxLines   how many spans may take a line of their own
     */
    static TraceTreePlan of(
            List<TraceSpanRow> spans, List<TraceSpanRunRow> runs, long traceNanos, int maxLines) {

        long floorNanos = Math.max(LEAF_FLOOR_NANOS, traceNanos / LEAF_FLOOR_SHARE_DENOMINATOR);

        // A span whose only children were folded on the server still has children: it is structure,
        // never a promoted leaf.
        Set<String> parents = new HashSet<>();
        for (TraceSpanRow span : spans) {
            if (span.parentSpanId() != null) {
                parents.add(span.parentSpanId());
            }
        }
        for (TraceSpanRunRow run : runs) {
            if (run.parentSpanId() != null) {
                parents.add(run.parentSpanId());
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

        // A server run renders where the server placed it, if its parent made it into the tree; one
        // of roots always does.
        Map<Integer, List<TraceSpanRunRow>> runsAt = new HashMap<>();
        for (TraceSpanRunRow run : runs) {
            boolean parentRendered = run.parentSpanId() == null || rendered.contains(run.parentSpanId());
            if (parentRendered) {
                runsAt.computeIfAbsent(run.position(), _ -> new ArrayList<>()).add(run);
            } else {
                omitted += (int) run.durations().count();
            }
        }

        // Lines in tree order: a fold takes the place of the first leaf it swallowed, while the
        // budget for fold lines lasts; the groups past it are counted on one closing line.
        FoldBudget budget = new FoldBudget(maxLines);
        List<Row> rows = new ArrayList<>(rendered.size() + Math.min(folds.size() + runs.size(), maxLines) + 1);
        Set<FoldKey> emitted = new HashSet<>();
        for (int index = 0; index < spans.size(); index++) {
            for (TraceSpanRunRow run : runsAt.getOrDefault(index, List.of())) {
                budget.add(rows, runLine(run));
            }
            TraceSpanRow span = spans.get(index);
            if (rendered.contains(span.spanId())) {
                rows.add(new SpanLine(span));
                continue;
            }
            FoldKey key = foldOf.get(span.spanId());
            if (key != null && emitted.add(key)) {
                budget.add(rows, folds.get(key).freeze());
            }
        }
        for (TraceSpanRunRow run : runsAt.getOrDefault(spans.size(), List.of())) {
            budget.add(rows, runLine(run));
        }
        if (budget.collapsedGroups > 0) {
            rows.add(new CollapsedFolds(budget.collapsedGroups, budget.collapsedLeaves));
        }
        int folded = budget.folded;
        return new TraceTreePlan(List.copyOf(rows), folded, omitted);
    }

    /** A promoted span with nothing under it -- a wait or an I/O operation, never a method. */
    private static boolean isPromotedLeaf(TraceSpanRow span, Set<String> parents) {
        return span.synthesized() && !parents.contains(span.spanId());
    }

    private static FoldLine runLine(TraceSpanRunRow run) {
        return new FoldLine(
                run.name(),
                run.kind(),
                run.depth(),
                (int) run.durations().count(),
                run.durations().totalNanos(),
                run.durations().maxNanos(),
                CLASS_LOADING_ORIGIN.equals(run.ioOrigin()));
    }

    /** How many fold lines may still be emitted, and what the ones past that added up to. */
    private static final class FoldBudget {
        private final int maxLines;
        private int lines;
        private int folded;
        private int collapsedGroups;
        private int collapsedLeaves;

        private FoldBudget(int maxLines) {
            this.maxLines = maxLines;
        }

        private void add(List<Row> rows, FoldLine line) {
            if (++lines <= maxLines) {
                folded += line.count();
                rows.add(line);
            } else {
                collapsedGroups++;
                collapsedLeaves += line.count();
            }
        }
    }

    /** The lines to render, in tree order. */
    List<Row> rows() {
        return rows;
    }

    /** How many leaves were folded into {@link FoldLine}s, not counting collapsed ones. */
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
