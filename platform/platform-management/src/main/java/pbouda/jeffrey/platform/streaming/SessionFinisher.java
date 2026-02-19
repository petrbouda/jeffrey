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

package pbouda.jeffrey.platform.streaming;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.platform.jfr.JfrMessageEmitter;
import pbouda.jeffrey.platform.project.repository.SessionFinishEventEmitter;
import pbouda.jeffrey.provider.platform.repository.ProjectRepositoryRepository;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.ProjectInstanceSessionInfo;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Centralizes the logic for marking sessions as finished. Consolidates the 3 scattered
 * "mark session finished" code paths (polling detector, stream close, session auto-close).
 */
public class SessionFinisher {

    private static final Logger LOG = LoggerFactory.getLogger(SessionFinisher.class);

    private final Clock clock;
    private final SessionFinishEventEmitter eventEmitter;

    public SessionFinisher(Clock clock, SessionFinishEventEmitter eventEmitter) {
        this.clock = clock;
        this.eventEmitter = eventEmitter;
    }

    /**
     * Marks a session as finished with an explicit finish time.
     * Used when the exact finish time is known (e.g., from heartbeat or stream close).
     */
    public void markFinished(
            ProjectRepositoryRepository repositoryRepository,
            ProjectInfo projectInfo,
            ProjectInstanceSessionInfo sessionInfo,
            Instant finishedAt,
            Instant lastHeartbeatAt) {

        if (lastHeartbeatAt != null) {
            repositoryRepository.markSessionFinishedWithHeartbeat(
                    sessionInfo.sessionId(), finishedAt, lastHeartbeatAt);
        } else {
            repositoryRepository.markSessionFinished(sessionInfo.sessionId(), finishedAt);
        }

        eventEmitter.emitSessionFinished(projectInfo, sessionInfo);
        JfrMessageEmitter.sessionFinished(sessionInfo.sessionId(), projectInfo.id());
    }

    /**
     * Determines the finish time from heartbeat data and marks session finished.
     * Used by polling-based detection and auto-close of previous sessions.
     *
     * @return true if session was marked finished
     */
    public boolean tryFinishFromHeartbeat(
            ProjectRepositoryRepository repositoryRepository,
            ProjectInfo projectInfo,
            ProjectInstanceSessionInfo sessionInfo,
            Path sessionPath,
            Duration heartbeatThreshold,
            Instant fallbackFinishedAt) {

        Instant lastHeartbeat = sessionInfo.lastHeartbeatAt();

        // Case 1: Heartbeat exists in DB
        if (lastHeartbeat != null) {
            if (lastHeartbeat.isBefore(clock.instant().minus(heartbeatThreshold))) {
                markFinished(repositoryRepository, projectInfo, sessionInfo, lastHeartbeat, lastHeartbeat);
                return true;
            }
            return false;
        }

        // Case 2: Session too young, heartbeats may not have arrived
        if (sessionInfo.originCreatedAt().isAfter(clock.instant().minus(heartbeatThreshold))) {
            return false;
        }

        // Case 3: Replay heartbeat from streaming repo
        Optional<Instant> replayed = HeartbeatReplayReader.readLastHeartbeat(
                sessionPath, sessionInfo.originCreatedAt(), clock);

        if (replayed.isPresent()) {
            repositoryRepository.updateLastHeartbeat(sessionInfo.sessionId(), replayed.get());
            if (replayed.get().isBefore(clock.instant().minus(heartbeatThreshold))) {
                markFinished(repositoryRepository, projectInfo, sessionInfo, replayed.get(), replayed.get());
                return true;
            }
            return false;
        }

        // Case 4: No heartbeat anywhere -- use fallback
        markFinished(repositoryRepository, projectInfo, sessionInfo, fallbackFinishedAt, null);
        return true;
    }
}
