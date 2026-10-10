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
        ProjectInfo projectInfo = ref.project();
        ProjectInstanceSessionInfo sessionInfo = ref.session();
        ProjectRepositoryRepository repositoryRepository =
                platformRepositories.newProjectRepositoryRepository(projectInfo.id());

        repositoryRepository.markSessionFinished(sessionInfo.sessionId(), finishedAt);

        LOG.info("Session marked as FINISHED: session_id={} project_id={} finished_at={}",
                sessionInfo.sessionId(), projectInfo.id(), finishedAt);

        // Check if instance should transition to FINISHED (last active session done)
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
     *
     * <p>This is the only way a session that never reported liveness is finished: it wrote no
     * liveness files, so there is nothing for {@link #tryFinishFromHeartbeat} to read, and the
     * arrival of the instance's next session is the only evidence the hub has that the previous
     * one ended. Until then it is flagged as missing its heartbeat, and not taken as live.</p>
     */
    public void forceFinish(SessionRef ref, Instant fallbackFinishedAt) {
        Instant finishedAt = fileHeartbeatReader.readFinishedMarker(ref.path()).timestamp()
                .or(() -> fileHeartbeatReader.readLastHeartbeat(ref.path()).timestamp())
                .orElse(fallbackFinishedAt);
        markFinished(ref, finishedAt);
    }

    /**
     * Applies the deadlines to one unfinished session: marks it finished when it has stopped
     * reporting, and flags it as missing its heartbeat when it never started reporting. Used by the
     * polling detector.
     *
     * <p>Every session is expected to report liveness. The writer is the Jeffrey agent the
     * Provisioner attaches by default, or, where a deployment switched the agent off, the
     * {@code jeffrey-heartbeat} library the application carries itself. A session that wrote no
     * liveness file within the startup grace is flagged as missing its heartbeat: heartbeats are
     * not configured or not emitted, and nothing on the hub's side can tell whether its JVM runs,
     * so it is not taken as live. It is not finished for staying silent either — the profiler may
     * still be writing into it — and is closed when the instance's next session appears, by
     * {@link #forceFinish}.</p>
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
     *   <li>no liveness file past the startup grace — flagged as missing its heartbeat, once.</li>
     * </ol>
     *
     * <p>A liveness file appearing after the flag was set clears it: the session reports after
     * all, and is held to the heartbeat deadline from then on.</p>
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
            clearHeartbeatMissing(ref);
            markFinished(ref, markerAt);
            return true;
        }

        // Case 2: the JVM stopped beating without writing the clean-exit marker
        LivenessRead lastHeartbeat = fileHeartbeatReader.readLastHeartbeat(sessionPath);
        if (lastHeartbeat instanceof LivenessRead.Reported(Instant heartbeatAt)) {
            clearHeartbeatMissing(ref);
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

        // Case 5: silent past the grace — heartbeats are not configured or not emitted
        if (!sessionInfo.heartbeatMissing()) {
            repositoryRepository(ref).setSessionHeartbeatMissing(sessionInfo.sessionId(), true);
            LOG.info("Session reported no heartbeat within the startup grace, not taken as live: "
                            + "session_id={} project_id={} created_at={}",
                    sessionInfo.sessionId(), ref.project().id(), sessionInfo.createdAt());
        }
        return false;
    }

    private void clearHeartbeatMissing(SessionRef ref) {
        ProjectInstanceSessionInfo sessionInfo = ref.session();
        if (sessionInfo.heartbeatMissing()) {
            repositoryRepository(ref).setSessionHeartbeatMissing(sessionInfo.sessionId(), false);
            LOG.info("Session reports liveness after all, heartbeat no longer missing: session_id={} project_id={}",
                    sessionInfo.sessionId(), ref.project().id());
        }
    }

    private ProjectRepositoryRepository repositoryRepository(SessionRef ref) {
        return platformRepositories.newProjectRepositoryRepository(ref.project().id());
    }
}
