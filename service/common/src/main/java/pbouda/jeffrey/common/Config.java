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

package pbouda.jeffrey.common;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public record Config(
        Type type,
        String primaryId,
        String secondaryId,
        List<Path> primaryRecordings,
        List<Path> secondaryRecordings,
        pbouda.jeffrey.common.Type eventType,
        ProfilingStartEnd primaryStartEnd,
        ProfilingStartEnd secondaryStartEnd,
        AbsoluteTimeRange timeRange,
        String searchPattern,
        boolean threadMode,
        boolean collectWeight,
        boolean excludeNonJavaSamples,
        boolean excludeIdleSamples,
        // Parses Line and Bytecode of the give StackFrame
        boolean parseLocations) {

    public enum Type {
        PRIMARY, DIFFERENTIAL
    }

    public Config(
            Type type,
            String primaryId,
            List<Path> primaryRecordings,
            pbouda.jeffrey.common.Type eventType,
            ProfilingStartEnd primaryStartEnd,
            AbsoluteTimeRange timeRange,
            String searchPattern,
            boolean threadMode,
            boolean collectWeight,
            boolean excludeNonJavaSamples,
            boolean excludeIdleSamples,
            boolean parseLocations) {

        this(type, primaryId, null, primaryRecordings, null, eventType, primaryStartEnd, null, timeRange,
                searchPattern, threadMode, collectWeight, excludeNonJavaSamples, excludeIdleSamples, parseLocations);
    }

    public static ConfigBuilder<?> primaryBuilder() {
        return new ConfigBuilder<>();
    }

    public static DiffConfigBuilder differentialBuilder() {
        return new DiffConfigBuilder();
    }

    /**
     * Difference between the start of the primary and secondary recording.
     *
     * @return positive or negative duration between the primary and secondary recording
     */
    public Duration timeShift() {
        return Duration.between(primaryStartEnd.start(), secondaryStartEnd.start());
    }

    public Config copyWithType(pbouda.jeffrey.common.Type eventType) {
        return new Config(
                type, primaryId, secondaryId, primaryRecordings, secondaryRecordings, eventType, primaryStartEnd,
                secondaryStartEnd, timeRange, searchPattern, threadMode, collectWeight,
                excludeNonJavaSamples, excludeIdleSamples, parseLocations);
    }
}
