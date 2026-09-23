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

package cafe.jeffrey.flamegraph.diff;

import cafe.jeffrey.flamegraph.export.AiExportConfig;
import cafe.jeffrey.flamegraph.export.WeightContext;
import cafe.jeffrey.frameir.DiffFrame;
import cafe.jeffrey.microscope.model.Type;

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

        return report(eventType, root, primaryDuration, baselineDuration, limit, true);
    }

    public static ComparisonReport report(Type eventType, DiffFrame root, Duration primaryDuration,
                                          Duration baselineDuration, int limit, boolean useWeight) {
        WeightContext weight = WeightContext.of(eventType, useWeight);
        ComparisonScale scale = scaleOf(weight, root, primaryDuration, baselineDuration);
        return DiffgraphAnalyzer.analyze(eventType, root, scale, limit, weight);
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

        return rankedMarkdown(eventType, root, primaryDuration, baselineDuration, limit, true);
    }

    public static String rankedMarkdown(Type eventType, DiffFrame root, Duration primaryDuration,
                                        Duration baselineDuration, int limit, boolean useWeight) {
        return new ComparisonMarkdownBuilder(
                report(eventType, root, primaryDuration, baselineDuration, limit, useWeight)).build();
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

        return treeMarkdown(eventType, root, primaryDuration, baselineDuration, config, true);
    }

    public static String treeMarkdown(Type eventType, DiffFrame root, Duration primaryDuration,
                                      Duration baselineDuration, AiExportConfig config, boolean useWeight) {
        WeightContext weight = WeightContext.of(eventType, useWeight);
        ComparisonScale scale = scaleOf(weight, root, primaryDuration, baselineDuration);
        return new DiffgraphAiMarkdownBuilder(eventType, scale, config, weight).build(root);
    }

    /**
     * The totals the whole comparison is normalised against, read off the diff tree's root so they are
     * exactly the totals of the tree being described — including any filter the graph parameters
     * applied, which a profile-wide event count would have ignored.
     */
    private static ComparisonScale scaleOf(
            WeightContext weight, DiffFrame root, Duration primaryDuration, Duration baselineDuration) {

        DiffMeasure measure = new DiffMeasure(weight);
        long primaryTotal = root == null ? 0L : measure.primary(root);
        long baselineTotal = root == null ? 0L : measure.baseline(root);
        // The sample count, which is what the thin-sample warnings are about: a weighted total can be
        // large while the samples behind it are few, and that is exactly the comparison worth warning
        // about. Named as the measurement rather than through an event type the unweighted context
        // does not consult.
        DiffMeasure samples = new DiffMeasure(WeightContext.UNWEIGHTED);
        return new ComparisonScale(primaryDuration, baselineDuration, primaryTotal, baselineTotal,
                root == null ? 0L : samples.primary(root), root == null ? 0L : samples.baseline(root));
    }
}
