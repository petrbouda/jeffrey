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

package cafe.jeffrey.performance.analyst.settings;

/**
 * The analysis knobs that belong to a project rather than to the whole installation.
 *
 * <p>{@code pruneThresholdPct} controls how much of the call tree reaches the model: a service with one
 * obvious hotspot reads better coarse, while diffuse cost needs a finer tree to be actionable at all.
 * {@code regressionThresholdPp} is the percentage-point move a frame must make before a comparison
 * calls it a regression — below it, normal sampling noise would fill the view with false alarms.</p>
 *
 * @param pruneThresholdPct     minimum share of total samples for a frame to appear in the prompt
 * @param regressionThresholdPp minimum percentage-point change for a frame to count as moved
 */
public record ProjectAiSettings(double pruneThresholdPct, double regressionThresholdPp) {

    private static final double DEFAULT_PRUNE_THRESHOLD_PCT = 1.0;
    private static final double DEFAULT_REGRESSION_THRESHOLD_PP = 2.0;

    public ProjectAiSettings {
        if (pruneThresholdPct <= 0 || pruneThresholdPct >= 100) {
            throw new IllegalArgumentException(
                    "Prune threshold must be between 0 and 100 exclusive: " + pruneThresholdPct);
        }
        if (regressionThresholdPp < 0) {
            throw new IllegalArgumentException(
                    "Regression threshold must not be negative: " + regressionThresholdPp);
        }
    }

    public static ProjectAiSettings defaults() {
        return new ProjectAiSettings(DEFAULT_PRUNE_THRESHOLD_PCT, DEFAULT_REGRESSION_THRESHOLD_PP);
    }
}
