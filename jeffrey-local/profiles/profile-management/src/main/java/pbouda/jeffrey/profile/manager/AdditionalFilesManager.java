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

package pbouda.jeffrey.profile.manager;

import pbouda.jeffrey.shared.common.model.ProfileInfo;
import pbouda.jeffrey.profile.manager.model.PerfCounter;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public interface AdditionalFilesManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, AdditionalFilesManager> {
    }

    void processAdditionalFiles(String recordingId);

    boolean performanceCountersExists();

    List<PerfCounter> performanceCounters();

    /**
     * Check if a heap dump file exists for this recording.
     *
     * @return true if a heap dump file is available
     */
    boolean heapDumpExists();

    /**
     * Get the path to the heap dump file if one exists.
     *
     * @return path to heap dump, or empty if not available
     */
    Optional<Path> getHeapDumpPath();

    /**
     * Get the path to the heap dump analysis folder for this profile.
     *
     * @return path to the heap-dump-analysis folder
     */
    Path getHeapDumpAnalysisPath();
}
