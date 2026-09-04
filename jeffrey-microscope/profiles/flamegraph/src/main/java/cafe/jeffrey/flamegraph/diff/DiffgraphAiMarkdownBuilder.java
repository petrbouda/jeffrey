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

package cafe.jeffrey.flamegraph.diff;

import cafe.jeffrey.flamegraph.ai.AiExportConfig;
import cafe.jeffrey.flamegraph.ai.WeightContext;
import cafe.jeffrey.frameir.DiffFrame;
import cafe.jeffrey.shared.common.model.Type;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders a differential call tree as Markdown — the drill-down a reader reaches for once
 * {@link ComparisonMarkdownBuilder} has told them which method moved.
 * <p>
 * Pruning is by movement, not by size, and that is the whole difference from the single-profile
 * export. A diff tree pruned by weight keeps the application's biggest call paths, which are mostly
 * the ones that did not change; the handful of frames that did move are small in absolute terms and
 * get cut. So a subtree survives when <em>anything inside it</em> moved by more than the threshold,
 * which also keeps the unmoved ancestors needed to place it in the call tree.
 */
public final class DiffgraphAiMarkdownBuilder {

    private static final String PREAMBLE = """
            # How to read this differential call tree

            A **differential flamegraph** of two profiles of the same application, exported by
            Jeffrey. One is the **primary** — the run under examination — and the other is the
            **baseline** it is measured against. A positive delta means the primary spends *more*
            than the baseline did: a regression.

            ## Read the comparability section first

            The arithmetic below is always valid; the comparison is only valid if both recordings
            watched the same kind of work, which nothing in a recording can prove. If the
            comparability section lists warnings, weigh them before reporting any finding.

            ## Line format

            A markdown nested bullet list. Every kept frame appears exactly once, two spaces of
            indentation per level of call depth: a bullet nested under another was called by it.

                - <method> [<state>] — Δ<delta> (<change>) · primary <p> (<p%>) · baseline <b> -> <b'> (<b%>)

            - `[S]` — **shared**: the frame is present in both profiles.
            - `[NEW]` — present only in the primary. Its whole subtree is new work; it has no
              children in this document because there is nothing to pair it against.
            - `[GONE]` — present only in the baseline. Its whole subtree disappeared.
            - `Δ<delta>` — the movement: primary total minus scaled baseline total, in the unit
              named by `unit` (or `weight_unit` on a weighted event type).
            - `(<change>)` — that movement as a percentage of the scaled baseline. Absent on a
              `[NEW]` or `[GONE]` frame, where there is no baseline to be a percentage of.
            - `primary <p> (<p%>)` — the frame's **whole subtree** in the primary, and its share of
              the primary's total. This is the bar width in the visual flamegraph.
            - `baseline <b> -> <b'> (<b%>)` — the same subtree in the baseline: raw, then scaled onto
              the primary's time base, then its share of the baseline's total.
            - Children are sorted by the size of the movement inside them, largest first, so the
              path to what changed is always the first nested bullet.

            ## Totals here, self elsewhere

            The numbers on each line are **subtree totals**, like any flamegraph. A change deep in a
            call path therefore shows up on every ancestor above it with the same delta — that is
            how a tree works, not five separate regressions. Walk down until the delta stops being
            explained by a child: that frame is where the work actually moved. The companion ranked
            list orders methods by *self* movement and does this attribution for you.

            ## Pruning

            Subtrees in which nothing moved by more than `prune_threshold_pct` of the larger of the
            two profiles' totals are dropped entirely. **Absence therefore means "did not move", not "not present"** —
            the opposite of the single-profile export, where absence means "small". A frame that is
            large in both profiles and changed in neither will not appear here at all.

            ## Renames read as a pair of dramatic findings

            The tree is built by matching method names level by level, so a renamed, moved or
            extracted method breaks the match: its work appears once as `[NEW]` and once as
            `[GONE]`, often of near-identical size. Before reporting either half as a real change,
            check the source diff — which you have and this document does not.

            ---
            """;

    private static final String TREE_HEADING = "## Differential call tree";
    private static final String COMPARABILITY_HEADING = "## Comparability";
    private static final String COMPARABILITY_CLEAN =
            "No comparability problems detected: the two recordings are of similar length and carry "
                    + "comparable volumes of this event type.";

    private static final String EMPTY_TREE_NOTE =
            "(nothing moved by more than the prune threshold — the two profiles agree on this event "
                    + "type to within that margin)";

    private static final String ROOT_LABEL = "[root]";
    private static final String INDENT_UNIT = "  ";
    private static final String BULLET_PREFIX = "- ";
    private static final String DASH_SEPARATOR = " — ";
    private static final String CLAUSE_SEPARATOR = " · ";
    private static final String SCALE_ARROW = " -> ";
    private static final String DELTA_PREFIX = "Δ";

    private static final String STATE_SHARED = "S";
    private static final String STATE_NEW = "NEW";
    private static final String STATE_GONE = "GONE";

    private final Type eventType;
    private final WeightContext weightContext;
    private final DiffMeasure measure;
    private final ComparisonScale scale;
    private final AiExportConfig config;

    /**
     * Identity-keyed on purpose: {@link DiffFrame} extends {@code TreeMap}, so its {@code hashCode} is
     * its whole content and walking it would cost more than the memo saves — and two structurally
     * equal subtrees at different places in the tree would collide.
     */
    private final Map<DiffFrame, Long> movementCache = new IdentityHashMap<>();

    public DiffgraphAiMarkdownBuilder(Type eventType, ComparisonScale scale, AiExportConfig config) {
        this.eventType = eventType;
        this.weightContext = WeightContext.of(eventType);
        this.measure = new DiffMeasure(weightContext);
        this.scale = scale;
        this.config = config;
    }

    public String build(DiffFrame root) {
        StringBuilder out = new StringBuilder(8192);
        out.append(PREAMBLE).append('\n');
        renderHeader(out);
        out.append('\n');
        renderComparability(out);
        out.append('\n');
        renderTree(out, root);
        return out.toString();
    }

    private void renderHeader(StringBuilder out) {
        out.append("event_type: ").append(eventType.code()).append('\n');
        out.append("unit: ").append(weightContext.unit()).append('\n');
        if (weightContext.weighted()) {
            out.append("weight_unit: ").append(weightContext.weightUnit()).append('\n');
            out.append("measured_by: weight\n");
        } else {
            out.append("measured_by: samples\n");
        }
        out.append("primary_duration: ").append(scale.primaryDuration()).append('\n');
        out.append("baseline_duration: ").append(scale.baselineDuration()).append('\n');
        out.append("scaled: ").append(scale.scaled() ? "yes" : "no").append('\n');
        out.append("baseline_scale_factor: ")
                .append(String.format(Locale.ROOT, "%.3f", scale.factor())).append('\n');
        out.append("primary_total: ").append(weightContext.format(scale.primaryTotal())).append('\n');
        out.append("baseline_total: ").append(weightContext.format(scale.baselineTotal()));
        if (scale.scaled()) {
            out.append(" (scaled: ").append(weightContext.format(scale.scaledBaselineTotal())).append(')');
        }
        out.append('\n');
        out.append("prune_threshold_pct: ").append(config.minFrameThresholdPct()).append('\n');
    }

    private void renderComparability(StringBuilder out) {
        out.append(COMPARABILITY_HEADING).append('\n').append('\n');
        List<String> warnings = scale.warnings();
        if (warnings.isEmpty()) {
            out.append(COMPARABILITY_CLEAN).append('\n');
            return;
        }
        for (String warning : warnings) {
            out.append("- **").append(warning).append("**\n");
        }
    }

    private void renderTree(StringBuilder out, DiffFrame root) {
        out.append(TREE_HEADING).append('\n').append('\n');
        if (root == null) {
            out.append(BULLET_PREFIX).append(EMPTY_TREE_NOTE).append('\n');
            return;
        }

        long minMovement = minMovement();
        renderRootLine(out, root);

        List<DiffFrame> survivors = survivingChildren(root, minMovement);
        if (survivors.isEmpty()) {
            out.append(INDENT_UNIT).append(BULLET_PREFIX).append(EMPTY_TREE_NOTE).append('\n');
            return;
        }
        for (DiffFrame child : survivors) {
            renderFrame(out, child, 1, minMovement);
        }
    }

    /**
     * The movement a subtree must contain to be worth printing: a share of the larger of the two
     * profiles, so the threshold means the same thing here as it does in the single-profile export and
     * still has a denominator when one of the two sides recorded nothing.
     */
    private long minMovement() {
        return Math.round(scale.referenceTotal() * config.minFrameThresholdPct() / 100.0);
    }

    private void renderRootLine(StringBuilder out, DiffFrame root) {
        out.append(BULLET_PREFIX).append(ROOT_LABEL);
        renderMetrics(out, root);
        out.append('\n');
    }

    private void renderFrame(StringBuilder out, DiffFrame node, int depth, long minMovement) {
        out.append(INDENT_UNIT.repeat(depth)).append(BULLET_PREFIX);
        out.append(sanitize(node.methodName));
        out.append(" [").append(stateOf(node)).append(']');
        renderMetrics(out, node);
        out.append('\n');

        for (DiffFrame child : survivingChildren(node, minMovement)) {
            renderFrame(out, child, depth + 1, minMovement);
        }
    }

    private void renderMetrics(StringBuilder out, DiffFrame node) {
        long primary = measure.primary(node);
        long baseline = measure.baseline(node);
        long scaledBaseline = scale.scaleBaseline(baseline);
        long delta = primary - scaledBaseline;

        out.append(DASH_SEPARATOR).append(DELTA_PREFIX).append(signed(delta));
        if (scaledBaseline > 0) {
            out.append(" (").append(String.format(
                    Locale.ROOT, "%+.1f%%", 100.0 * delta / scaledBaseline)).append(')');
        }

        out.append(CLAUSE_SEPARATOR).append("primary ").append(weightContext.format(primary));
        out.append(" (").append(formatPercent(scale.primarySharePct(primary))).append(')');

        out.append(CLAUSE_SEPARATOR).append("baseline ").append(weightContext.format(baseline));
        if (scale.scaled()) {
            out.append(SCALE_ARROW).append(weightContext.format(scaledBaseline));
        }
        out.append(" (").append(formatPercent(scale.baselineSharePct(baseline))).append(')');
    }

    private static String stateOf(DiffFrame node) {
        return switch (node.type) {
            case SHARED -> STATE_SHARED;
            case ADDED -> STATE_NEW;
            case REMOVED -> STATE_GONE;
        };
    }

    private List<DiffFrame> survivingChildren(DiffFrame node, long minMovement) {
        List<DiffFrame> survivors = new ArrayList<>();
        for (DiffFrame child : node.values()) {
            if (movement(child) >= minMovement) {
                survivors.add(child);
            }
        }
        survivors.sort(Comparator.comparingLong(this::movement).reversed());
        return survivors;
    }

    /**
     * The largest movement anywhere inside a subtree — what decides whether it is printed at all. Taken
     * over the subtree rather than at the node so an unchanged frame that merely sits above a changed
     * one is kept: without its ancestors, a moved frame cannot be placed in the call tree.
     */
    private long movement(DiffFrame node) {
        Long cached = movementCache.get(node);
        if (cached != null) {
            return cached;
        }
        long largest = Math.abs(measure.primary(node) - scale.scaleBaseline(measure.baseline(node)));
        for (DiffFrame child : node.values()) {
            largest = Math.max(largest, movement(child));
        }
        movementCache.put(node, largest);
        return largest;
    }

    private String signed(long delta) {
        return (delta < 0 ? "-" : "+") + weightContext.format(Math.abs(delta));
    }

    private static String formatPercent(double pct) {
        return String.format(Locale.ROOT, "%.1f%%", pct);
    }

    private static String sanitize(String title) {
        if (title == null) {
            return "?";
        }
        return title.replace(';', '_').replace('\n', '_').replace('\r', '_');
    }
}
