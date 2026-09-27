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

package cafe.jeffrey.flamegraph.export;

/**
 * How the Markdown export of one flamegraph is cut down for a reader.
 *
 * @param minFrameThresholdPct the share of the profile below which a subtree is pruned from the tree,
 *                             exclusive range 0-100
 * @param view                 a tree or a summary; the summary lists frames by self and prunes nothing,
 *                             so it does not read the threshold
 */
public record AiExportConfig(double minFrameThresholdPct, AiExportView view) {

    /**
     * The threshold a summary carries only because the record requires one. Never read: a summary has
     * no tree to prune.
     */
    private static final double SUMMARY_THRESHOLD_PCT = 1.0;

    public AiExportConfig {
        if (!(minFrameThresholdPct > 0.0 && minFrameThresholdPct < 100.0)) {
            throw new IllegalArgumentException(
                    "minFrameThresholdPct must be in (0, 100): " + minFrameThresholdPct);
        }
        if (view == null) {
            throw new IllegalArgumentException("view must not be null");
        }
    }

    /** The call tree, pruned at this threshold. */
    public AiExportConfig(double minFrameThresholdPct) {
        this(minFrameThresholdPct, AiExportView.TREE);
    }

    /** The summary: top frames by self and the heaviest paths, no tree. */
    public static AiExportConfig summary() {
        return new AiExportConfig(SUMMARY_THRESHOLD_PCT, AiExportView.SUMMARY);
    }
}
