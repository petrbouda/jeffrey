/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.platform.manager.qanalysis;

import pbouda.jeffrey.profile.manager.ProfileManager;
import pbouda.jeffrey.shared.common.model.ProfileInfo;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Manager for quick/ad-hoc JFR analysis that bypasses the workspace/project structure.
 * Quick analysis profiles are stored in the temp directory and are automatically
 * cleaned up when the application restarts.
 */
public interface QuickAnalysisManager {

    /**
     * Uploads and analyzes a JFR file.
     * The file is saved to the quick-recordings directory and then parsed.
     *
     * @param filename    the original filename
     * @param inputStream the file content stream
     * @return a CompletableFuture containing the profile ID
     */
    CompletableFuture<String> uploadAndAnalyze(String filename, InputStream inputStream);

    /**
     * Uploads a heap dump file and creates a lightweight profile.
     * No JFR parsing is performed - just stores the heap dump for analysis.
     *
     * @param filename    the original filename (must end with .hprof or .hprof.gz)
     * @param inputStream the file content stream
     * @return a CompletableFuture containing the profile ID
     */
    CompletableFuture<String> uploadHeapDump(String filename, InputStream inputStream);

    /**
     * Lists all quick analysis profiles that are currently available.
     *
     * @return list of profile info for quick analysis profiles
     */
    List<ProfileInfo> listProfiles();

    /**
     * Gets a profile manager for a quick analysis profile.
     *
     * @param profileId the profile ID
     * @return optional profile manager if found
     */
    Optional<ProfileManager> profile(String profileId);

    /**
     * Deletes a quick analysis profile.
     *
     * @param profileId the profile ID to delete
     */
    void deleteProfile(String profileId);
}
