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

package cafe.jeffrey.profile.manager.model.nativememory;

import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;

/**
 * Native dynamic-library load/unload activity from {@code jdk.NativeLibraryLoad} and
 * {@code jdk.NativeLibraryUnload} (JDK 24+). Unlike the static {@code jdk.NativeLibrary} list, these
 * carry the load/unload {@code duration} (slow-startup contributors) and a {@code success} flag with
 * an {@code errorMessage} (failed {@code dlopen}/JNI loads — a real startup bug).
 *
 * @param header     headline counters
 * @param operations individual load/unload operations, slowest first, capped
 * @param timeline   per-second load and unload counts ({@code Loads} / {@code Unloads} series)
 */
public record NativeLibraryActivityData(
        Header header,
        List<LibraryOperation> operations,
        TimeseriesData timeline) {

    public enum Operation {
        LOAD,
        UNLOAD
    }

    /**
     * @param totalLoads      number of load operations
     * @param failedLoads     loads whose {@code success} flag was false
     * @param totalUnloads    number of unload operations
     * @param slowestLoadNanos duration of the slowest load
     * @param totalLoadNanos  summed load duration (approximate startup cost of native linking)
     * @param slowestLibrary  name of the slowest-loading library ({@code null} when none)
     */
    public record Header(
            long totalLoads,
            long failedLoads,
            long totalUnloads,
            long slowestLoadNanos,
            long totalLoadNanos,
            String slowestLibrary) {
    }

    /**
     * A single native-library load or unload.
     *
     * @param operation       LOAD or UNLOAD
     * @param name            library name/path
     * @param timeOffsetMillis offset from the recording start
     * @param durationNanos   load/unload duration
     * @param success         whether the operation succeeded
     * @param errorMessage    failure description when {@code success} is false ({@code null} otherwise)
     */
    public record LibraryOperation(
            Operation operation,
            String name,
            long timeOffsetMillis,
            long durationNanos,
            boolean success,
            String errorMessage) {
    }
}
