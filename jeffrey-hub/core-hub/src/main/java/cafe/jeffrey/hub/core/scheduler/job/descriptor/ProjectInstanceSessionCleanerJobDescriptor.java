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

package cafe.jeffrey.hub.core.scheduler.job.descriptor;

import cafe.jeffrey.shared.common.model.job.JobType;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Retention rules for finished instance sessions: an age window ({@code duration} +
 * {@code timeUnit}) and a per-instance cap of {@code maxSessions} logical sessions.
 * A logical session is either a single session that produced data or a consecutive
 * run of failed-empty sessions (a crash loop), which counts as one.
 */
public record ProjectInstanceSessionCleanerJobDescriptor(
        long duration,
        ChronoUnit timeUnit,
        int maxSessions
) implements JobDescriptor<ProjectInstanceSessionCleanerJobDescriptor> {

    private static final String PARAM_DURATION = "duration";
    private static final String PARAM_TIME_UNIT = "time-unit";
    private static final String PARAM_MAX_SESSIONS = "max-sessions";

    public ProjectInstanceSessionCleanerJobDescriptor {
        if (maxSessions <= 0) {
            throw new IllegalArgumentException(PARAM_MAX_SESSIONS + " must be positive: " + maxSessions);
        }
    }

    @Override
    public Map<String, String> params() {
        return Map.of(
                PARAM_DURATION, String.valueOf(duration),
                PARAM_TIME_UNIT, timeUnit.toString(),
                PARAM_MAX_SESSIONS, Integer.toString(maxSessions));
    }

    @Override
    public JobType type() {
        return JobType.PROJECT_INSTANCE_SESSION_CLEANER;
    }

    public Duration toDuration() {
        return Duration.of(duration, timeUnit);
    }

    public static ProjectInstanceSessionCleanerJobDescriptor of(Map<String, String> params) {
        return new ProjectInstanceSessionCleanerJobDescriptor(
                JobDescriptorUtils.resolveLong(params, PARAM_DURATION),
                JobDescriptorUtils.resolveChronoUnit(params, PARAM_TIME_UNIT),
                JobDescriptorUtils.resolveInt(params, PARAM_MAX_SESSIONS));
    }
}
