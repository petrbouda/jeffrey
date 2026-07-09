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

package cafe.jeffrey.hub.core.scheduler.job.descriptor;

import cafe.jeffrey.shared.common.model.job.JobType;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;

public record ProjectInstanceRecordingCleanerJobDescriptor(
        long duration,
        ChronoUnit timeUnit
) implements JobDescriptor<ProjectInstanceRecordingCleanerJobDescriptor> {

    private static final String PARAM_DURATION = "duration";
    private static final String PARAM_TIME_UNIT = "time-unit";

    @Override
    public Map<String, String> params() {
        return Map.of(
                PARAM_DURATION, String.valueOf(duration),
                PARAM_TIME_UNIT, timeUnit.toString());
    }

    @Override
    public JobType type() {
        return JobType.PROJECT_INSTANCE_RECORDING_CLEANER;
    }

    public Duration toDuration() {
        return Duration.of(duration, timeUnit);
    }

    public static ProjectInstanceRecordingCleanerJobDescriptor of(Map<String, String> params) {
        return new ProjectInstanceRecordingCleanerJobDescriptor(
                JobDescriptorUtils.resolveLong(params, PARAM_DURATION),
                JobDescriptorUtils.resolveChronoUnit(params, PARAM_TIME_UNIT));
    }
}
