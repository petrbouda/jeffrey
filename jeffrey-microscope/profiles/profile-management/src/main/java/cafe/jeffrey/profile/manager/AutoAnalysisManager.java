/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package cafe.jeffrey.profile.manager;

import cafe.jeffrey.profile.common.analysis.AutoAnalysisResult;
import cafe.jeffrey.shared.common.model.ProfileInfo;

import java.util.List;
import java.util.function.Function;

public interface AutoAnalysisManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, AutoAnalysisManager> {
    }

    /**
     * The findings already computed for this profile, read from the profile's cache. Empty when
     * nothing has computed them yet -- the analysis is warmed when the profile is imported, so this
     * is normally populated by the time anyone asks.
     */
    List<AutoAnalysisResult> analysisResults();

    /**
     * Whether the analysis can be run at all, which comes down to whether the recording file the JMC
     * rule set reads is still on disk. Asked before warming so a profile whose recording is gone is
     * skipped quietly rather than failing.
     */
    boolean canGenerate();

    /**
     * Runs the rule set and caches the findings. Single-flight: a caller arriving while a run is in
     * progress joins that run instead of starting a second one.
     */
    List<AutoAnalysisResult> generate();

}
