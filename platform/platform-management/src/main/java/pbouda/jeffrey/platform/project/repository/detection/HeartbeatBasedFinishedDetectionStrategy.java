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

package pbouda.jeffrey.platform.project.repository.detection;

import pbouda.jeffrey.shared.common.model.repository.RecordingStatus;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Detection strategy that uses heartbeat events to determine session liveness.
 * If heartbeat data is available: a recent heartbeat means ACTIVE, a stale heartbeat means FINISHED.
 * If no heartbeat data is available: delegates to the fallback strategy.
 */
public record HeartbeatBasedFinishedDetectionStrategy(
        Instant lastHeartbeatAt,
        Clock clock,
        FinishedDetectionStrategy fallback
) implements FinishedDetectionStrategy {

    private static final Duration HEARTBEAT_THRESHOLD = Duration.ofSeconds(15);

    @Override
    public RecordingStatus determineStatus(Path sessionPath) {
        if (lastHeartbeatAt != null) {
            Instant threshold = clock.instant().minus(HEARTBEAT_THRESHOLD);
            if (lastHeartbeatAt.isAfter(threshold)) {
                return RecordingStatus.ACTIVE;
            } else {
                return RecordingStatus.FINISHED;
            }
        }
        return fallback.determineStatus(sessionPath);
    }
}
