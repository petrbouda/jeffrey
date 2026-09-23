/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.profile.manager.additional;

import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.profile.manager.additional.PerfCounter;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public interface AdditionalFilesManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, AdditionalFilesManager> {
    }

    /**
     * Takes in the recording's supplementary files — perf counters, heap dumps — and makes the
     * profile able to answer about them.
     * <p>
     * The files are handed over rather than looked up. A project recording keeps them in its own
     * directory, a downloaded one keeps them flat beside its recording files, and the caller is
     * the one that knows which; the lookup that used to live here only ever found the first kind,
     * so everything a hub download brought along was silently inert.
     *
     * @param artifacts the recording's artifact files; an empty list is a recording with none
     */
    void processAdditionalFiles(List<Path> artifacts);

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
     * @return path to the heap-dump folder
     */
    Path getHeapDumpAnalysisPath();
}
