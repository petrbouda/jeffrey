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

package cafe.jeffrey.hub.core.scheduler.history;

import cafe.jeffrey.shared.common.model.job.JobType;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Immutable record of one finished job run: what ran, when, how long it took,
 * how it ended, and the human-readable details the job reported.
 *
 * @param jobType   the job that ran
 * @param startedAt when the run started
 * @param duration  measured wall-clock duration of the run
 * @param status    how the run ended
 * @param summary   one-line description of what the run did, or {@code null} if the
 *                  job reported nothing
 * @param items     individual detail lines (deleted project names, processed events, ...)
 * @param error     failure description, or {@code null} for successful runs
 */
public record JobExecution(
        JobType jobType,
        Instant startedAt,
        Duration duration,
        JobExecutionStatus status,
        String summary,
        List<String> items,
        String error) {

    public JobExecution {
        if (jobType == null) {
            throw new IllegalArgumentException("jobType must not be null");
        }
        if (startedAt == null) {
            throw new IllegalArgumentException("startedAt must not be null");
        }
        if (duration == null) {
            throw new IllegalArgumentException("duration must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        items = items == null ? List.of() : List.copyOf(items);
    }

    /**
     * A run that completed successfully but reported nothing — typically a periodic
     * tick that found no work to do.
     */
    public boolean noop() {
        return status == JobExecutionStatus.SUCCESS && summary == null && items.isEmpty();
    }
}
