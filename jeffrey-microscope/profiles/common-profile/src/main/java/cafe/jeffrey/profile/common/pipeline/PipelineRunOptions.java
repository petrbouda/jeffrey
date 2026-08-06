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

package cafe.jeffrey.profile.common.pipeline;

import java.time.Duration;

/**
 * The two ways pipelines legitimately differ in how their runs are governed.
 *
 * <p>Both defaults say "no limit", because a limit should be something a pipeline asks for rather than
 * something it inherits. A ceiling only earns its keep when a run holds a scarce external resource: the
 * heap dump's work is local CPU and IO, so capping it would only serialise a user against themselves,
 * while an AI run holds a provider call open for minutes and a handful of them at once will hit a rate
 * limit. Likewise a TTL is right when a finished run is superseded by something durable, and wrong when
 * the run summary itself is what a page displays.</p>
 *
 * @param maxConcurrentRuns how many runs may execute at once across all keys, or {@link #UNBOUNDED}
 * @param completedRunTtl   how long a finished run stays queryable before eviction, or {@code null} to
 *                          keep it until the next run for that key replaces it
 */
public record PipelineRunOptions(int maxConcurrentRuns, Duration completedRunTtl) {

    public static final int UNBOUNDED = Integer.MAX_VALUE;

    public PipelineRunOptions {
        if (maxConcurrentRuns < 1) {
            throw new IllegalArgumentException(
                    "At least one concurrent run must be allowed: " + maxConcurrentRuns);
        }
        if (completedRunTtl != null && (completedRunTtl.isNegative() || completedRunTtl.isZero())) {
            throw new IllegalArgumentException("Completed-run TTL must be positive: " + completedRunTtl);
        }
    }

    /** Keeps the last run per key forever and lets any number run at once. */
    public static PipelineRunOptions unbounded() {
        return new PipelineRunOptions(UNBOUNDED, null);
    }

    public static PipelineRunOptions bounded(int maxConcurrentRuns, Duration completedRunTtl) {
        return new PipelineRunOptions(maxConcurrentRuns, completedRunTtl);
    }

    public boolean evictsFinishedRuns() {
        return completedRunTtl != null;
    }
}
