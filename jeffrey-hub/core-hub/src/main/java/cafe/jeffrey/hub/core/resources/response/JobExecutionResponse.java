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

package cafe.jeffrey.hub.core.resources.response;

import cafe.jeffrey.hub.core.scheduler.history.JobExecution;
import cafe.jeffrey.hub.core.scheduler.history.JobExecutionStatus;
import cafe.jeffrey.shared.common.model.job.JobType;

import java.util.List;

/**
 * One entry of the in-memory scheduler job execution history.
 *
 * @param startedAt UTC epoch millis of the run start
 */
public record JobExecutionResponse(
        JobType jobType,
        JobType.ExecutionLevel executionLevel,
        long startedAt,
        long durationMs,
        JobExecutionStatus status,
        boolean noop,
        String summary,
        List<String> items,
        String error) {

    public static JobExecutionResponse from(JobExecution execution) {
        return new JobExecutionResponse(
                execution.jobType(),
                execution.jobType().executionLevel(),
                execution.startedAt().toEpochMilli(),
                execution.duration().toMillis(),
                execution.status(),
                execution.noop(),
                execution.summary(),
                execution.items(),
                execution.error());
    }
}
