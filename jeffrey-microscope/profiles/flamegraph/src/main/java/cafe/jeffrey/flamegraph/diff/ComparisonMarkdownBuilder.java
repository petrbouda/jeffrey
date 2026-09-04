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

import cafe.jeffrey.flamegraph.ai.WeightContext;

import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;

/**
 * Renders a {@link ComparisonReport} as Markdown for a model to read.
 * <p>
 * The preamble is the substance here, not decoration. Handed a bare table of deltas a reader will
 * report the largest one as a regression, and will be wrong whenever the two recordings differed in
 * length, in workload or in profiler configuration — none of which is visible in the numbers
 * themselves. So the document states what was measured, what correction was applied, and which
 * conclusions the data cannot support, before it shows a single number.
 */
public final class ComparisonMarkdownBuilder {

    private static final String PREAMBLE = """
            # How to read this comparison

            Two profiles of the same application, compared by Jeffrey. One is the **primary** — the
            run under examination — and the other is the **baseline** it is measured against. A
            positive delta means the primary spends *more* than the baseline did: a regression.

            ## Read the comparability section first

            Everything below is arithmetic on two independent recordings. The arithmetic is always
            valid; the *comparison* is only valid if both recordings watched the same kind of work.
            Nothing in a JFR file proves that they did. If the comparability section lists warnings,
            weigh them before reporting any finding — a warned comparison can be worse than no
            comparison, because the numbers look equally precise either way.

            ## Scaling

            A sampling profiler emits samples at a roughly fixed rate, so a recording that ran twice
            as long holds about twice as many samples of the same steady workload. Baseline values
            are therefore multiplied by `baseline_scale_factor` (= primary duration / baseline
            duration) before any delta is taken, and both the raw and the scaled figure are shown.
            When the header says `baseline_scale_factor: 1.000` and `scaled: no`, no correction could
            be made and the deltas compare raw counts.

            This correction assumes a steady workload measured over time. It is wrong for a
            fixed-size benchmark — N requests replayed in both runs — where the honest normaliser is
            per-request and Jeffrey cannot know it. On such a run, read the **share** column instead.

            ## The columns

            - **primary self** / **baseline self** — work that stopped *at* this method, not in
              anything it called. Self, not total, because a total-based delta charges a change to
              every caller above it: one slow leaf would report `main`, the thread-pool runnable and
              every framework frame in between as having regressed by the same amount. Self weight
              moves only where the work moved.
            - **baseline self** shows `raw -> scaled` when a scale factor was applied.
            - **delta** — primary self minus scaled baseline self, in the unit named by `unit` (or
              `weight_unit` on a weighted event type).
            - **change** — that delta as a percentage of the scaled baseline. `new` means the method
              carried no work in the baseline at all: that is a different kind of finding from a
              large percentage, not an infinite one.
            - **share delta** — percentage *points* of the whole profile this method gained or lost.
              This is the column to trust when the comparability section is unhappy: it asks where
              the profile went, not how big it was. A method can regress in share while the process
              as a whole got faster, and vice versa.
            - **paths** — how many distinct call paths reached this method. Numbers are aggregated
              per method across all of them, because a method that got slower usually got slower
              everywhere it is called from.
            - **example path** — the single call path that contributed most to the movement, so the
              method can be found in the source. Truncated to its last few frames.

            ## Candidate renames

            The diff is built by matching method names level by level, so a renamed, moved or
            extracted method breaks the match and its work appears twice: once as entirely new, once
            as entirely gone. Pairs of similar size are listed as **candidate renames**. They are
            suspicions, not conclusions — weight alone cannot tell a rename from a coincidence. You
            have the source diff, which this document does not; check it before reporting either half
            of such a pair as a real change.

            ## What this document cannot tell you

            - Whether the application got faster overall. This is one event type's distribution, not
              a wall-clock benchmark. A CPU profile that shifted work into a shorter path may still
              have regressed end-to-end latency, and vice versa.
            - Whether a difference is significant. There is no repetition here and no confidence
              interval; a single pair of recordings cannot separate a real 5% move from run-to-run
              variance. Small movements on thin profiles are noise.
            - Why something changed. Correlate the movements with the source diff; the profile says
              where, never why.

            ---
            """;

    private static final String COMPARABILITY_HEADING = "## Comparability";
    private static final String COMPARABILITY_CLEAN =
            "No comparability problems detected: the two recordings are of similar length and carry "
                    + "comparable volumes of this event type. The deltas below can be read as stated — "
                    + "bearing in mind that a single pair of runs still cannot separate a small "
                    + "movement from run-to-run variance.";

    private static final String REGRESSED_HEADING = "## Regressed — the primary spends more here";
    private static final String IMPROVED_HEADING = "## Improved — the primary spends less here";
    private static final String RENAMES_HEADING = "## Candidate renames (unconfirmed)";

    private static final String MOVERS_TABLE_HEADER = """
            | method | primary self | baseline self | delta | change | share delta | paths | example path |
            |---|---|---|---|---|---|---|---|
            """;

    private static final String RENAMES_TABLE_HEADER = """
            | appeared | at | measure | vanished | at | measure (scaled) |
            |---|---|---|---|---|---|
            """;

    private static final String NO_REGRESSIONS =
            "Nothing regressed: no method carries more work in the primary than in the baseline.";
    private static final String NO_IMPROVEMENTS =
            "Nothing improved: no method carries less work in the primary than in the baseline.";
    private static final String NOTHING_COMPARED =
            "Neither profile recorded any work for this event type, so there is nothing to compare. "
                    + "Check that both recordings were made with the same profiler configuration.";

    private static final String CHANGE_NEW = "new";
    private static final String CHANGE_GONE = "gone";
    private static final String SCALE_ARROW = " -> ";

    private final ComparisonReport report;

    public ComparisonMarkdownBuilder(ComparisonReport report) {
        this.report = report;
    }

    public String build() {
        StringBuilder out = new StringBuilder(8192);
        out.append(PREAMBLE).append('\n');
        renderHeader(out);
        out.append('\n');
        renderComparability(out);

        if (report.empty()) {
            out.append('\n').append(NOTHING_COMPARED).append('\n');
            return out.toString();
        }

        renderMovers(out, REGRESSED_HEADING, report.regressed(), NO_REGRESSIONS);
        renderMovers(out, IMPROVED_HEADING, report.improved(), NO_IMPROVEMENTS);
        renderRenames(out);
        return out.toString();
    }

    private void renderHeader(StringBuilder out) {
        ComparisonScale scale = report.scale();
        WeightContext weight = report.weightContext();

        out.append("event_type: ").append(report.eventType().code()).append('\n');
        out.append("unit: ").append(weight.unit()).append('\n');
        if (weight.weighted()) {
            out.append("weight_unit: ").append(weight.weightUnit()).append('\n');
            out.append("measured_by: weight\n");
        } else {
            out.append("measured_by: samples\n");
        }
        out.append("primary_duration: ").append(scale.primaryDuration()).append('\n');
        out.append("baseline_duration: ").append(scale.baselineDuration()).append('\n');
        out.append("scaled: ").append(scale.scaled() ? "yes" : "no").append('\n');
        out.append("baseline_scale_factor: ")
                .append(String.format(Locale.ROOT, "%.3f", scale.factor())).append('\n');
        out.append("primary_total: ").append(weight.format(scale.primaryTotal())).append('\n');
        out.append("baseline_total: ").append(weight.format(scale.baselineTotal()));
        if (scale.scaled()) {
            out.append(" (scaled: ").append(weight.format(scale.scaledBaselineTotal())).append(')');
        }
        out.append('\n');
        out.append("methods_compared: ").append(report.methodsCompared()).append('\n');
    }

    private void renderComparability(StringBuilder out) {
        out.append(COMPARABILITY_HEADING).append('\n').append('\n');
        List<String> warnings = report.scale().warnings();
        if (warnings.isEmpty()) {
            out.append(COMPARABILITY_CLEAN).append('\n');
            return;
        }
        for (String warning : warnings) {
            out.append("- **").append(warning).append("**\n");
        }
    }

    private void renderMovers(StringBuilder out, String heading, List<MethodDelta> movers, String emptyNote) {
        out.append('\n').append(heading).append('\n').append('\n');
        if (movers.isEmpty()) {
            out.append(emptyNote).append('\n');
            return;
        }
        out.append(MOVERS_TABLE_HEADER);
        for (MethodDelta mover : movers) {
            renderMover(out, mover);
        }
    }

    private void renderMover(StringBuilder out, MethodDelta mover) {
        ComparisonScale scale = report.scale();
        WeightContext weight = report.weightContext();

        out.append("| ").append(sanitize(mover.methodName()))
                .append(" | ").append(weight.format(mover.primarySelf()))
                .append(" | ").append(baselineCell(mover))
                .append(" | ").append(signed(mover.delta(scale)))
                .append(" | ").append(changeCell(mover))
                .append(" | ").append(signedPoints(mover.shareDeltaPoints(scale)))
                .append(" | ").append(mover.callPaths())
                .append(" | ").append(sanitize(mover.examplePath()))
                .append(" |\n");
    }

    private String baselineCell(MethodDelta mover) {
        ComparisonScale scale = report.scale();
        WeightContext weight = report.weightContext();
        String raw = weight.format(mover.baselineSelf());
        if (!scale.scaled()) {
            return raw;
        }
        return raw + SCALE_ARROW + weight.format(scale.scaleBaseline(mover.baselineSelf()));
    }

    private String changeCell(MethodDelta mover) {
        OptionalDouble change = mover.changePct(report.scale());
        if (change.isPresent()) {
            return String.format(Locale.ROOT, "%+.1f%%", change.getAsDouble());
        }
        if (mover.appeared()) {
            return CHANGE_NEW;
        }
        return CHANGE_GONE;
    }

    /** A delta reads as a movement, so it always carries its direction, formatted in its own unit. */
    private String signed(long delta) {
        String magnitude = report.weightContext().format(Math.abs(delta));
        return (delta < 0 ? "-" : "+") + magnitude;
    }

    private static String signedPoints(double points) {
        return String.format(Locale.ROOT, "%+.2f pp", points);
    }

    private void renderRenames(StringBuilder out) {
        List<RenameCandidate> candidates = report.renameCandidates();
        if (candidates.isEmpty()) {
            return;
        }
        WeightContext weight = report.weightContext();
        out.append('\n').append(RENAMES_HEADING).append('\n').append('\n');
        out.append(RENAMES_TABLE_HEADER);
        for (RenameCandidate candidate : candidates) {
            out.append("| ").append(sanitize(candidate.appearedMethod()))
                    .append(" | ").append(sanitize(candidate.appearedPath()))
                    .append(" | ").append(weight.format(candidate.appearedMeasure()))
                    .append(" | ").append(sanitize(candidate.vanishedMethod()))
                    .append(" | ").append(sanitize(candidate.vanishedPath()))
                    .append(" | ").append(weight.format(candidate.vanishedMeasure()))
                    .append(" |\n");
        }
    }

    /** Keeps a cell on one table row: a pipe in a method name would otherwise split the row. */
    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('|', '/').replace('\n', ' ').replace('\r', ' ');
    }
}
