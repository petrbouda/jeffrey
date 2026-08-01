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
 * What an advisor run needs to know to analyze a profile.
 *
 * <p>{@code sourcePath} belongs to a project rather than to the whole installation: every profile of a
 * service shares a working copy, but only the person running Microscope knows where it is, and it is
 * the one setting without a usable default. Until it is set, the Generate action has nothing to read
 * and says so.</p>
 *
 * <p>{@code pruneThresholdPct} controls how much of the call tree reaches the model: a service with one
 * obvious hotspot reads better coarse, while diffuse cost needs a finer tree to be actionable at all.
 * It is an installation-wide preference, resolved from the global Advisor settings by
 * {@link AdvisorSettingsResolver}, not stored per project.</p>
 *
 * @param sourcePath        absolute path to the project's working copy, or blank when unconfigured
 * @param pruneThresholdPct minimum share of total samples for a frame to appear in the prompt
 */
public record AdvisorSettings(
        String sourcePath,
        double pruneThresholdPct) {

    public static final double DEFAULT_PRUNE_THRESHOLD_PCT = 1.0;

    private static final String NO_VALUE = "";

    public AdvisorSettings {
        if (pruneThresholdPct <= 0 || pruneThresholdPct >= 100) {
            throw new IllegalArgumentException(
                    "Prune threshold must be between 0 and 100 exclusive: " + pruneThresholdPct);
        }
        sourcePath = sourcePath == null ? NO_VALUE : sourcePath.strip();
    }

    public boolean hasSourcePath() {
        return !sourcePath.isBlank();
    }
}
