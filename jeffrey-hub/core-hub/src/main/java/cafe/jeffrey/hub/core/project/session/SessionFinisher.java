/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.hub.core.project.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.core.jfr.JfrNotificationEmitter;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
import cafe.jeffrey.hub.persistence.api.ProjectInstanceRepository;
import cafe.jeffrey.hub.persistence.api.ProjectRepositoryRepository;
import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.hub.model.ProjectInstanceInfo.ProjectInstanceStatus;
import cafe.jeffrey.hub.model.ProjectInstanceSessionInfo;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

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
    public void markFinished(SessionRef ref, Instant finishedAt) {
        ProjectRepositoryRepository repositoryRepository = repositoryRepository(ref);
        repositoryRepository.markSessionFinished(ref.session().sessionId(), finishedAt);

        LOG.info("Session marked as FINISHED: session_id={} project_id={} finished_at={}",
                ref.session().sessionId(), ref.project().id(), finishedAt);

        afterFinished(ref, repositoryRepository, finishedAt);
    }

    /**
     * Marks a session as finished at its start, because no heartbeat arrived within the startup
     * grace. {@code createdAt} rather than {@code originCreatedAt}, because the timeline starts the
     * session's bar there and a producer-clock timestamp could fall before it.
     */
    private void markFinishedWithoutHeartbeat(SessionRef ref) {
        Instant finishedAt = ref.session().createdAt();
        ProjectRepositoryRepository repositoryRepository = repositoryRepository(ref);
        repositoryRepository.markSessionFinishedWithoutHeartbeat(ref.session().sessionId(), finishedAt);

        LOG.info("Session sent no heartbeat within the startup grace, marked as FINISHED at its start: "
                        + "session_id={} project_id={} finished_at={}",
                ref.session().sessionId(), ref.project().id(), finishedAt);

        afterFinished(ref, repositoryRepository, finishedAt);
    }

    /**
     * What follows any finish: the instance becomes FINISHED with its last open session, and the
     * finish is announced.
     */
    private void afterFinished(
            SessionRef ref, ProjectRepositoryRepository repositoryRepository, Instant finishedAt) {

        ProjectInfo projectInfo = ref.project();
        ProjectInstanceSessionInfo sessionInfo = ref.session();

        List<ProjectInstanceSessionInfo> remaining =
                repositoryRepository.findUnfinishedSessionsByInstanceId(sessionInfo.instanceId());
        if (remaining.isEmpty()) {
            ProjectInstanceRepository instanceRepo =
                    platformRepositories.newProjectInstanceRepository(projectInfo.id());
            instanceRepo.updateStatusAndFinishedAt(
                    sessionInfo.instanceId(), ProjectInstanceStatus.FINISHED, finishedAt);
            LOG.info("Instance marked as FINISHED (last session done): instance_id={} project_id={}",
                    sessionInfo.instanceId(), projectInfo.id());
        }

        JfrNotificationEmitter.sessionFinished(sessionInfo.sessionId(), projectInfo.id());
    }

    /**
     * Unconditionally finishes a session using the heartbeat file for the finish timestamp,
     * or the provided fallback if no heartbeat is available. No staleness check is performed.
     * Used when closing previous sessions before creating a new one.
     */
    public void forceFinish(SessionRef ref, Instant fallbackFinishedAt) {
        Instant finishedAt = fileHeartbeatReader.readFinishedMarker(ref.path()).timestamp()
                .or(() -> fileHeartbeatReader.readLastHeartbeat(ref.path()).timestamp())
                .orElse(fallbackFinishedAt);
        markFinished(ref, finishedAt);
    }

    /**
     * Applies the deadlines to one unfinished session and marks it finished when it has stopped
     * reporting, or never started reporting. Used by the polling detector.
     *
     * <p>Every session must report liveness. The writer is the Jeffrey agent the Provisioner
     * attaches by default, or, where a deployment switched the agent off, the
     * {@code jeffrey-heartbeat} library the application carries itself.</p>
     *
     * <p>Five outcomes:</p>
     * <ol>
     *   <li>the clean-exit marker is present — finished at the timestamp it carries. Checked
     *   first and by presence alone, so it is immune to clock skew between the producing host
     *   and the hub: the producer-written timestamp is recorded as the finish time, never
     *   compared against the hub clock;</li>
     *   <li>the heartbeat has gone stale — finished at the last heartbeat, which is when the
     *   JVM was last known alive. This is the crash path, where nothing closed the library;</li>
     *   <li>a liveness file could not be read — left alone. A file that is there and unreadable
     *   says nothing about whether the JVM is running, and any timestamp would be a fabrication
     *   rather than a reading. The next sweep looks again;</li>
     *   <li>no liveness file, and the hub saw the session less than the startup grace ago — left
     *   alone: the JVM may still be starting;</li>
     *   <li>no liveness file past the startup grace — heartbeats are not configured or not
     *   emitted. Finished at its start and marked as having ended without a heartbeat, so the UI
     *   can say how to turn heartbeats on.</li>
     * </ol>
     *
     * <p>The startup grace is measured against {@code createdAt} — the hub's own clock at
     * materialization — so the comparison is skew-free.</p>
     *
     * @return true if session was marked finished
     */
    public boolean tryFinishFromHeartbeat(SessionRef ref, SessionDeadlines deadlines) {
        ProjectInstanceSessionInfo sessionInfo = ref.session();
        Path sessionPath = ref.path();

        // Case 1: clean-exit marker, written when the application shut down cleanly
        LivenessRead finishedMarker = fileHeartbeatReader.readFinishedMarker(sessionPath);
        if (finishedMarker instanceof LivenessRead.Reported(Instant markerAt)) {
            LOG.trace("Clean-exit marker found, marking finished: session_id={}", sessionInfo.sessionId());
            markFinished(ref, markerAt);
            return true;
        }

        // Case 2: the JVM stopped beating without writing the clean-exit marker
        LivenessRead lastHeartbeat = fileHeartbeatReader.readLastHeartbeat(sessionPath);
        if (lastHeartbeat instanceof LivenessRead.Reported(Instant heartbeatAt)) {
            Instant deadline = clock.instant().minus(deadlines.heartbeatThreshold());
            if (heartbeatAt.isBefore(deadline)) {
                LOG.trace("Stale heartbeat, marking finished: session_id={} last_heartbeat={}",
                        sessionInfo.sessionId(), heartbeatAt);
                markFinished(ref, heartbeatAt);
                return true;
            }
            LOG.trace("Fresh heartbeat, session still alive: session_id={}", sessionInfo.sessionId());
            return false;
        }

        // Case 3: a read failed rather than came back empty. Nothing is known about this session,
        // so nothing is concluded about it — the next sweep reads again once the volume recovers.
        if (!finishedMarker.isAbsent() || !lastHeartbeat.isAbsent()) {
            LOG.warn("Liveness files unreadable, leaving session unfinished: session_id={} "
                            + "session_path={} finished_marker={} heartbeat={}",
                    sessionInfo.sessionId(), sessionPath, finishedMarker, lastHeartbeat);
            return false;
        }

        // Case 4: nothing reports liveness yet, and the JVM may still be starting
        Instant startupDeadline = clock.instant().minus(deadlines.startupGrace());
        if (sessionInfo.createdAt().isAfter(startupDeadline)) {
            LOG.trace("No liveness reported yet, still within startup grace: session_id={} created_at={}",
                    sessionInfo.sessionId(), sessionInfo.createdAt());
            return false;
        }

        // Case 5: no heartbeat past the grace — heartbeats are not configured or not emitted
        markFinishedWithoutHeartbeat(ref);
        return true;
    }

    private ProjectRepositoryRepository repositoryRepository(SessionRef ref) {
        return platformRepositories.newProjectRepositoryRepository(ref.project().id());
    }
}
