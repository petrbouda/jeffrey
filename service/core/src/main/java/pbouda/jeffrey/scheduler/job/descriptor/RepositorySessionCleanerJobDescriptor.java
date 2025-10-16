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

package pbouda.jeffrey.scheduler.job.descriptor;

import pbouda.jeffrey.common.model.job.JobType;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public record RepositorySessionCleanerJobDescriptor(
        long duration,
        ChronoUnit timeUnit
) implements JobDescriptor<RepositorySessionCleanerJobDescriptor> {

    private static final Map<String, ChronoUnit> CHRONO_UNITS = Arrays.stream(ChronoUnit.values())
            .collect(Collectors.toMap(ChronoUnit::toString, Function.identity()));

    private static final String PARAM_DURATION = "duration";
    private static final String PARAM_TIME_UNIT = "timeUnit";

    @Override
    public Map<String, String> params() {
        return Map.of(
                PARAM_DURATION, String.valueOf(duration),
                PARAM_TIME_UNIT, timeUnit.toString());
    }

    @Override
    public JobType type() {
        return JobType.REPOSITORY_SESSION_CLEANER;
    }

    public Duration toDuration() {
        return Duration.of(duration, timeUnit);
    }

    public static RepositorySessionCleanerJobDescriptor of(Map<String, String> params) {
        String durationStr = params.get(PARAM_DURATION);
        String timeUnit = params.get(PARAM_TIME_UNIT);
        return new RepositorySessionCleanerJobDescriptor(Long.parseLong(durationStr), parseTimeUnit(timeUnit));
    }

    private static ChronoUnit parseTimeUnit(String timeUnit) {
        ChronoUnit chronoUnit = CHRONO_UNITS.get(timeUnit);
        if (chronoUnit == null) {
            throw new IllegalArgumentException("Unknown time unit: " + timeUnit);
        }
        return chronoUnit;
    }
}
