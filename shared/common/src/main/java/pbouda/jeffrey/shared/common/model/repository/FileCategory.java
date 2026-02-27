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

package pbouda.jeffrey.shared.common.model.repository;

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
