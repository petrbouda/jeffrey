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

package cafe.jeffrey.hub.core.session.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.core.jfr.JfrNotificationEmitter;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
import cafe.jeffrey.hub.persistence.api.ProjectInstanceRepository;
import cafe.jeffrey.hub.persistence.api.ProjectRepositoryRepository;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.ProjectInstanceInfo.ProjectInstanceStatus;
import cafe.jeffrey.shared.common.model.ProjectInstanceSessionInfo;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Centralizes the logic for marking sessions as finished. Consolidates the scattered
 * "mark session finished" code paths (heartbeat polling, reconciliation and session auto-close).
 */
public class SessionFinisher {

    private static final Logger LOG = LoggerFactory.getLogger(SessionFinisher.class);

    private final Clock clock;
    private final FileHeartbeatReader fileHeartbeatReader;
    private final HubPlatformRepositories platformRepositories;

    public SessionFinisher(
            Clock clock,
            FileHeartbeatReader fileHeartbeatReader,
            HubPlatformRepositories platformRepositories) {

        this.clock = clock;
        this.fileHeartbeatReader = fileHeartbeatReader;
        this.platformRepositories = platformRepositories;
    }

    /**
     * Marks a session as finished with an explicit finish time.
     */
    public void markFinished(
            ProjectRepositoryRepository repositoryRepository,
            ProjectInfo projectInfo,
            ProjectInstanceSessionInfo sessionInfo,
            Instant finishedAt) {

        repositoryRepository.markSessionFinished(sessionInfo.sessionId(), finishedAt);

        LOG.info("Session marked as FINISHED: sessionId={} projectId={} finishedAt={}",
                sessionInfo.sessionId(), projectInfo.id(), finishedAt);

        // Check if instance should transition to FINISHED (last active session done)
        List<ProjectInstanceSessionInfo> remaining =
                repositoryRepository.findUnfinishedSessionsByInstanceId(sessionInfo.instanceId());
        if (remaining.isEmpty()) {
            ProjectInstanceRepository instanceRepo =
                    platformRepositories.newProjectInstanceRepository(projectInfo.id());
            instanceRepo.updateStatusAndFinishedAt(
                    sessionInfo.instanceId(), ProjectInstanceStatus.FINISHED, finishedAt);
            LOG.info("Instance marked as FINISHED (last session done): instanceId={} projectId={}",
                    sessionInfo.instanceId(), projectInfo.id());
        }

        JfrNotificationEmitter.sessionFinished(sessionInfo.sessionId(), projectInfo.id());
    }

    /**
     * Unconditionally finishes a session using the heartbeat file for the finish timestamp,
     * or the provided fallback if no heartbeat is available. No staleness check is performed.
     * Used when closing previous sessions before creating a new one.
     *
     * <p>This is the only way a session that never declared the Jeffrey agent is finished: it
     * writes no liveness files, so there is nothing for {@link #tryFinishFromHeartbeat} to
     * read, and the arrival of the instance's next session is the only evidence the hub has
     * that the previous one ended.</p>
     */
    public void forceFinish(
            ProjectRepositoryRepository repositoryRepository,
            ProjectInfo projectInfo,
            ProjectInstanceSessionInfo sessionInfo,
            Path sessionPath,
            Instant fallbackFinishedAt) {

        Instant finishedAt = fileHeartbeatReader.readFinishedMarker(sessionPath)
                .or(() -> fileHeartbeatReader.readLastHeartbeat(sessionPath))
                .orElse(fallbackFinishedAt);
        markFinished(repositoryRepository, projectInfo, sessionInfo, finishedAt);
    }

    /**
     * Applies the heartbeat deadline to one unfinished session and marks it finished when it
     * has stopped reporting. Used by the polling detector.
     *
     * <p>Only a session that declared the Jeffrey agent is held to this deadline. The agent is
     * the only writer of {@code .heartbeat/}, and it is not attached to every run
     * ({@code agent-path} is empty unless the deployment ships one), so a session that never
     * promised to report liveness must not be finished for failing to report it — those are
     * closed instead when the instance's next session appears, by
     * {@link #forceFinish}.</p>
     *
     * <p>Three outcomes for a session that did declare one:</p>
     * <ol>
     *   <li>the clean-exit marker is present — finished at the timestamp it carries. Checked
     *   first and by presence alone, so it is immune to clock skew between the producing host
     *   and the hub: the producer-written timestamp is recorded as the finish time, never
     *   compared against the hub clock;</li>
     *   <li>the heartbeat has gone stale — finished at the last heartbeat, which is when the
     *   JVM was last known alive. This is the crash path, where no shutdown hook ran;</li>
     *   <li>no liveness file at all past the deadline — the agent never got as far as writing
     *   one (a crash before premain, or a mount the JVM could not write to). Finished at
     *   {@code originCreatedAt}: a real timestamp the session actually has, rather than the
     *   moment this sweep happened to notice.</li>
     * </ol>
     *
     * <p>The deadline in case 3 is measured against {@code createdAt} — the hub's own clock at
     * materialization — and not against {@code originCreatedAt}, which the producer wrote. The
     * comparison is then skew-free even though the timestamp it records is not; case 2 keeps
     * the skew sensitivity the heartbeat file inherently has, and a hub-side freshness tracker
     * that remembers when each heartbeat value was first observed would remove it there too.</p>
     *
     * @return true if session was marked finished
     */
    public boolean tryFinishFromHeartbeat(
            ProjectRepositoryRepository repositoryRepository,
            ProjectInfo projectInfo,
            ProjectInstanceSessionInfo sessionInfo,
            Path sessionPath,
            Duration heartbeatThreshold) {

        if (!sessionInfo.declaresAgent()) {
            LOG.trace("Session declares no agent, no heartbeat deadline applies: sessionId={}",
                    sessionInfo.sessionId());
            return false;
        }

        // Case 1: clean-exit marker written by the agent's shutdown hook
        Optional<Instant> finishedMarker = fileHeartbeatReader.readFinishedMarker(sessionPath);
        if (finishedMarker.isPresent()) {
            LOG.trace("Clean-exit marker found, marking finished: sessionId={}", sessionInfo.sessionId());
            markFinished(repositoryRepository, projectInfo, sessionInfo, finishedMarker.get());
            return true;
        }

        Instant deadline = clock.instant().minus(heartbeatThreshold);

        // Case 2: the JVM stopped beating without running its shutdown hook
        Optional<Instant> lastHeartbeat = fileHeartbeatReader.readLastHeartbeat(sessionPath);
        if (lastHeartbeat.isPresent()) {
            Instant heartbeat = lastHeartbeat.get();
            if (heartbeat.isBefore(deadline)) {
                LOG.trace("Stale heartbeat, marking finished: sessionId={} lastHeartbeat={}",
                        sessionInfo.sessionId(), heartbeat);
                markFinished(repositoryRepository, projectInfo, sessionInfo, heartbeat);
                return true;
            }
            LOG.trace("Fresh heartbeat, session still alive: sessionId={}", sessionInfo.sessionId());
            return false;
        }

        // Case 3: the agent was declared but never wrote anything. Inside the deadline that is
        // a JVM still starting up; past it, one that never got to premain.
        if (sessionInfo.createdAt().isAfter(deadline)) {
            LOG.trace("No heartbeat yet, still within startup deadline: sessionId={} createdAt={}",
                    sessionInfo.sessionId(), sessionInfo.createdAt());
            return false;
        }

        LOG.trace("Declared agent never reported, marking finished at session start: sessionId={}",
                sessionInfo.sessionId());
        markFinished(repositoryRepository, projectInfo, sessionInfo, sessionInfo.originCreatedAt());
        return true;
    }
}
