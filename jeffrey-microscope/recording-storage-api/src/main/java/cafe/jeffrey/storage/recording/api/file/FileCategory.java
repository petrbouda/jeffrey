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

package cafe.jeffrey.storage.recording.api.file;

/**
 * Classifies files found in a recording session directory.
 */
public enum FileCategory {

    /**
     * Main profiling data files (JFR, JFR_LZ4) that are merged,
     * compressed, and downloaded as recordings.
     */
    RECORDING,

    /**
     * Supplementary files (heap dumps, JVM logs, perf counters)
     * that are copied alongside recordings during download.
     */
    ARTIFACT,

    /**
     * Unknown file types found in session directories — visible in session files
     * on InstanceDetail and downloadable, but not merged into recordings or copied as artifacts.
     */
    UNRECOGNIZED,

    /**
     * Transient processing files (e.g. async-profiler rotation artifacts like .jfr.1~)
     * that are visible in the UI but not downloadable.
     */
    TEMPORARY
}
