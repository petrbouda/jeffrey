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

import java.time.Duration;

/**
 * The two readable views of a differential call tree, and the one thing they must agree on.
 * <p>
 * Both the ranked list and the tree need the totals of the pair to normalise against, and both derive
 * them from the same place — the diff tree's root — through this entry point. A caller that assembled
 * its own {@link ComparisonScale} from, say, the profiles' overall event counts would be scaling
 * against a different denominator than the tree it is describing, and the percentages in the two
 * documents would quietly disagree.
 */
public final class ProfileComparison {

    private ProfileComparison() {
    }

    /**
     * The ranked movements between the two profiles: which methods grew, which shrank.
     *
     * @param limit how many movements to report in each direction
     */
    public static ComparisonReport report(
            Type eventType,
            DiffFrame root,
            Duration primaryDuration,
            Duration baselineDuration,
            int limit) {

        ComparisonScale scale = scaleOf(eventType, root, primaryDuration, baselineDuration);
        return DiffgraphAnalyzer.analyze(eventType, root, scale, limit);
    }

    /**
     * The same comparison rendered as a ranked Markdown document.
     */
    public static String rankedMarkdown(
            Type eventType,
            DiffFrame root,
            Duration primaryDuration,
            Duration baselineDuration,
            int limit) {

        return new ComparisonMarkdownBuilder(
                report(eventType, root, primaryDuration, baselineDuration, limit)).build();
    }

    /**
     * The differential call tree rendered as Markdown, pruned to the movements worth reading.
     */
    public static String treeMarkdown(
            Type eventType,
            DiffFrame root,
            Duration primaryDuration,
            Duration baselineDuration,
            AiExportConfig config) {

        ComparisonScale scale = scaleOf(eventType, root, primaryDuration, baselineDuration);
        return new DiffgraphAiMarkdownBuilder(eventType, scale, config).build(root);
    }

    /**
     * The totals the whole comparison is normalised against, read off the diff tree's root so they are
     * exactly the totals of the tree being described — including any filter the graph parameters
     * applied, which a profile-wide event count would have ignored.
     */
    private static ComparisonScale scaleOf(
            Type eventType, DiffFrame root, Duration primaryDuration, Duration baselineDuration) {

        DiffMeasure measure = new DiffMeasure(WeightContext.of(eventType));
        long primaryTotal = root == null ? 0L : measure.primary(root);
        long baselineTotal = root == null ? 0L : measure.baseline(root);
        return new ComparisonScale(primaryDuration, baselineDuration, primaryTotal, baselineTotal);
    }
}
