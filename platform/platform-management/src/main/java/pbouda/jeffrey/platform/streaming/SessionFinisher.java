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
 * Centralizes the logic for marking sessions as finished. Consolidates the scattered
 * "mark session finished" code paths (polling detector, stream close, session auto-close).
 */
public class SessionFinisher {

    private static final Logger LOG = LoggerFactory.getLogger(SessionFinisher.class);

    private final Clock clock;
    private final SessionFinishEventEmitter eventEmitter;
    private final FileHeartbeatReader fileHeartbeatReader;
    private final JfrStreamingConsumerManager streamingConsumerManager;

    public SessionFinisher(
            Clock clock,
            SessionFinishEventEmitter eventEmitter,
            FileHeartbeatReader fileHeartbeatReader,
            JfrStreamingConsumerManager streamingConsumerManager) {

        this.clock = clock;
        this.eventEmitter = eventEmitter;
        this.fileHeartbeatReader = fileHeartbeatReader;
        this.streamingConsumerManager = streamingConsumerManager;
    }

    /**
     * Marks a session as finished with an explicit finish time.
     * Also unregisters the streaming consumer for this session.
     */
    public void markFinished(
            ProjectRepositoryRepository repositoryRepository,
            ProjectInfo projectInfo,
            ProjectInstanceSessionInfo sessionInfo,
            Instant finishedAt) {

        repositoryRepository.markSessionFinished(sessionInfo.sessionId(), finishedAt);
        streamingConsumerManager.unregisterConsumer(sessionInfo.sessionId());

        LOG.info("Session marked as FINISHED: sessionId={} projectId={} finishedAt={}",
                sessionInfo.sessionId(), projectInfo.id(), finishedAt);

        eventEmitter.emitSessionFinished(projectInfo, sessionInfo);
        JfrMessageEmitter.sessionFinished(sessionInfo.sessionId(), projectInfo.id());
    }

    /**
     * Unconditionally finishes a session using the heartbeat file for the finish timestamp,
     * or the provided fallback if no heartbeat is available. No staleness check is performed.
     * Used when closing previous sessions before creating a new one.
     */
    public void forceFinish(
            ProjectRepositoryRepository repositoryRepository,
            ProjectInfo projectInfo,
            ProjectInstanceSessionInfo sessionInfo,
            Path sessionPath,
            Instant fallbackFinishedAt) {

        Optional<Instant> lastHeartbeat = fileHeartbeatReader.readLastHeartbeat(sessionPath);
        Instant finishedAt = lastHeartbeat.orElse(fallbackFinishedAt);
        markFinished(repositoryRepository, projectInfo, sessionInfo, finishedAt);
    }

    /**
     * Determines the finish time from heartbeat file and marks session finished.
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

        Optional<Instant> lastHeartbeat = fileHeartbeatReader.readLastHeartbeat(sessionPath);

        LOG.trace("tryFinishFromHeartbeat: sessionId={} heartbeatPresent={} heartbeatThreshold={}",
                sessionInfo.sessionId(), lastHeartbeat.isPresent(), heartbeatThreshold);

        // Case 1: Heartbeat file exists
        if (lastHeartbeat.isPresent()) {
            Instant hb = lastHeartbeat.get();
            if (hb.isBefore(clock.instant().minus(heartbeatThreshold))) {
                LOG.trace("Case 1 stale heartbeat, marking finished: sessionId={}", sessionInfo.sessionId());
                markFinished(repositoryRepository, projectInfo, sessionInfo, hb);
                return true;
            }
            LOG.trace("Case 1 fresh heartbeat, skipping: sessionId={}", sessionInfo.sessionId());
            return false;
        }

        // Case 2: No heartbeat file, session too young — heartbeats may not have arrived
        if (sessionInfo.originCreatedAt().isAfter(clock.instant().minus(heartbeatThreshold))) {
            LOG.trace("Case 2 session too young, skipping: sessionId={} originCreatedAt={}",
                    sessionInfo.sessionId(), sessionInfo.originCreatedAt());
            return false;
        }

        // Case 3: No heartbeat file, session old enough — use fallback
        LOG.trace("Case 3 no heartbeat found, using fallback: sessionId={} fallbackFinishedAt={}",
                sessionInfo.sessionId(), fallbackFinishedAt);
        markFinished(repositoryRepository, projectInfo, sessionInfo, fallbackFinishedAt);
        return true;
    }
}
