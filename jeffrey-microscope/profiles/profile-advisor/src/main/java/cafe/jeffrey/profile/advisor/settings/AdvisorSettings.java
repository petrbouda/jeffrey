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

package cafe.jeffrey.profile.advisor.settings;

/**
 * What an advisor run needs to know that belongs to a project rather than to the whole installation.
 *
 * <p>{@code sourcePath} is the one setting without a usable default: every profile of a service shares
 * a working copy, but only the person running Microscope knows where it is. Until it is set, the
 * Generate action has nothing to read and says so.</p>
 *
 * <p>{@code pruneThresholdPct} controls how much of the call tree reaches the model: a service with one
 * obvious hotspot reads better coarse, while diffuse cost needs a finer tree to be actionable at all.
 * {@code regressionThresholdPp} is the percentage-point move a frame must make before a comparison
 * calls it a regression — below it, normal sampling noise would fill the view with false alarms.</p>
 *
 * <p>{@code compileCommand} and {@code testCommand} are blank by default, and the verification ladder
 * reports the levels they drive as skipped rather than passed. Running a build is the user's decision
 * to make explicitly, not something a profiling tool should start doing on its own.</p>
 *
 * @param sourcePath            absolute path to the project's working copy, or blank when unconfigured
 * @param pruneThresholdPct     minimum share of total samples for a frame to appear in the prompt
 * @param regressionThresholdPp minimum percentage-point change for a frame to count as moved
 * @param compileCommand        command that compiles the patched source, or blank to skip that check
 * @param testCommand           command that runs the tests, or blank to skip that check
 */
public record AdvisorSettings(
        String sourcePath,
        double pruneThresholdPct,
        double regressionThresholdPp,
        String compileCommand,
        String testCommand) {

    public static final double DEFAULT_PRUNE_THRESHOLD_PCT = 1.0;
    public static final double DEFAULT_REGRESSION_THRESHOLD_PP = 2.0;

    private static final String NO_VALUE = "";

    public AdvisorSettings {
        if (pruneThresholdPct <= 0 || pruneThresholdPct >= 100) {
            throw new IllegalArgumentException(
                    "Prune threshold must be between 0 and 100 exclusive: " + pruneThresholdPct);
        }
        if (regressionThresholdPp < 0) {
            throw new IllegalArgumentException(
                    "Regression threshold must not be negative: " + regressionThresholdPp);
        }
        sourcePath = sourcePath == null ? NO_VALUE : sourcePath.strip();
        compileCommand = compileCommand == null ? NO_VALUE : compileCommand.strip();
        testCommand = testCommand == null ? NO_VALUE : testCommand.strip();
    }

    public static AdvisorSettings defaults() {
        return new AdvisorSettings(
                NO_VALUE, DEFAULT_PRUNE_THRESHOLD_PCT, DEFAULT_REGRESSION_THRESHOLD_PP, NO_VALUE, NO_VALUE);
    }

    public boolean hasSourcePath() {
        return !sourcePath.isBlank();
    }
}
