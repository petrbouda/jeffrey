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
import java.util.Map;

/**
 * Job descriptor for the session file detector job, which announces finished recording and
 * artifact files as workspace events.
 *
 * @param maxFileAge              how far back a file may be and still be announced. This is a
 *                               correctness bound, not a performance knob — see the job.
 * @param artifactSettleThreshold how long an artifact must have been untouched before it counts
 *                               as complete. Recordings get an ACTIVE marker from the repository
 *                               storage; artifacts do not, so a heap dump still being written
 *                               would otherwise be announced as finished.
 * @param maxEventsPerTick       cap on events emitted per project per tick
 */
public record SessionFileDetectorProjectJobDescriptor(
        Duration maxFileAge,
        Duration artifactSettleThreshold,
        int maxEventsPerTick
) implements JobDescriptor<SessionFileDetectorProjectJobDescriptor> {

    private static final String PARAM_MAX_FILE_AGE = "max-file-age";
    private static final String PARAM_ARTIFACT_SETTLE_THRESHOLD = "artifact-settle-threshold";
    private static final String PARAM_MAX_EVENTS_PER_TICK = "max-events-per-tick";

    public SessionFileDetectorProjectJobDescriptor {
        if (maxFileAge == null || maxFileAge.isNegative() || maxFileAge.isZero()) {
            throw new IllegalArgumentException(PARAM_MAX_FILE_AGE + " must be positive: " + maxFileAge);
        }
        if (artifactSettleThreshold == null || artifactSettleThreshold.isNegative()) {
            throw new IllegalArgumentException(
                    PARAM_ARTIFACT_SETTLE_THRESHOLD + " must not be negative: " + artifactSettleThreshold);
        }
        if (maxEventsPerTick <= 0) {
            throw new IllegalArgumentException(PARAM_MAX_EVENTS_PER_TICK + " must be positive: " + maxEventsPerTick);
        }
    }

    @Override
    public Map<String, String> params() {
        return Map.of(
                PARAM_MAX_FILE_AGE, maxFileAge.toString(),
                PARAM_ARTIFACT_SETTLE_THRESHOLD, artifactSettleThreshold.toString(),
                PARAM_MAX_EVENTS_PER_TICK, String.valueOf(maxEventsPerTick));
    }

    @Override
    public JobType type() {
        return JobType.SESSION_FILE_DETECTOR;
    }

    public static SessionFileDetectorProjectJobDescriptor of(Map<String, String> params) {
        return new SessionFileDetectorProjectJobDescriptor(
                JobDescriptorUtils.resolveDuration(params, PARAM_MAX_FILE_AGE),
                JobDescriptorUtils.resolveDuration(params, PARAM_ARTIFACT_SETTLE_THRESHOLD),
                JobDescriptorUtils.resolveInt(params, PARAM_MAX_EVENTS_PER_TICK));
    }
}
