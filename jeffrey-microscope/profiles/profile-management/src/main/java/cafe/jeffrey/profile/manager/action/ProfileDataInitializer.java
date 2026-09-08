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

package cafe.jeffrey.profile.manager.action;

import cafe.jeffrey.profile.common.analysis.AutoAnalysisResult;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.shared.common.model.ProfileInfo;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Warms the views a profile is expected to have ready by the time anyone opens it.
 * <p>
 * The two halves are called at opposite ends of the import, and deliberately so. The JMC rule set
 * reads the recording <em>file</em> and never the profile database, so it has nothing to wait for:
 * {@link #startAutoAnalysis} launches it before the parse, and {@link #initialize} collects it after
 * the database is written. The import therefore costs the longer of the two rather than both.
 */
public interface ProfileDataInitializer {

    /**
     * Starts the JMC rule set over the recording file, before it has been parsed.
     *
     * @return the findings, or {@code null} when the rules were skipped or failed. Never completes
     * exceptionally: the analysis is a cache, and a profile without it is a poorer profile rather
     * than a failed import.
     */
    CompletableFuture<List<AutoAnalysisResult>> startAutoAnalysis(ProfileInfo profileInfo, Path recordingPath);

    /**
     * Warms the remaining views and stores whatever {@link #startAutoAnalysis} produced.
     *
     * @param autoAnalysis the run started before the parse, joined here rather than started again
     * @return completes when every view has been warmed and the findings are cached. The import
     * pipeline waits for it, so that a profile which answers at all answers with its findings.
     */
    CompletableFuture<Void> initialize(
            ProfileManager profileManager, CompletableFuture<List<AutoAnalysisResult>> autoAnalysis);

    /**
     * Warms nothing, for the installation that switched the warming off. Every view is computed on
     * demand instead, so the first reader pays what the import would have.
     */
    static ProfileDataInitializer disabled() {
        return new ProfileDataInitializer() {

            @Override
            public CompletableFuture<List<AutoAnalysisResult>> startAutoAnalysis(
                    ProfileInfo profileInfo, Path recordingPath) {

                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<Void> initialize(
                    ProfileManager profileManager,
                    CompletableFuture<List<AutoAnalysisResult>> autoAnalysis) {

                return CompletableFuture.completedFuture(null);
            }
        };
    }
}
