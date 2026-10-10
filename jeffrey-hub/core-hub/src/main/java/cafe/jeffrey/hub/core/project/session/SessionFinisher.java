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
    private final SessionContentReader sessionContentReader;
    private final HubPlatformRepositories platformRepositories;

    public SessionFinisher(
            Clock clock,
            FileHeartbeatReader fileHeartbeatReader,
            SessionContentReader sessionContentReader,
            HubPlatformRepositories platformRepositories) {

        this.clock = clock;
        this.fileHeartbeatReader = fileHeartbeatReader;
        this.sessionContentReader = sessionContentReader;
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
     * <p>This is the only way a session that never reported liveness but did record something is
     * finished: it wrote no liveness files, so there is nothing for {@link #tryFinishFromHeartbeat}
     * to read, and the arrival of the instance's next session is the only evidence the hub has
     * that the previous one ended. A silent session that recorded nothing at all is finished by
     * {@link #tryFinishFromHeartbeat} past the startup grace instead.</p>
     */
    public void forceFinish(SessionRef ref, Instant fallbackFinishedAt) {
        Instant finishedAt = fileHeartbeatReader.readFinishedMarker(ref.path()).timestamp()
                .or(() -> fileHeartbeatReader.readLastHeartbeat(ref.path()).timestamp())
                .orElse(fallbackFinishedAt);
        markFinished(ref, finishedAt);
    }

    /**
     * Applies the deadlines to one unfinished session and marks it finished when it has stopped
     * reporting, or never started. Used by the polling detector.
     *
     * <p>A session is held to the heartbeat deadline only once it has shown that it reports
     * liveness, by writing a liveness file. The writer is the Jeffrey agent the Provisioner attaches
     * by default, or, where a deployment switched the agent off, the {@code jeffrey-heartbeat}
     * library the application carries itself; nothing declares up front whether either will
     * report. A session that never wrote a liveness file is held to the startup grace instead, and
     * only when it recorded nothing either.</p>
     *
     * <p>Six outcomes:</p>
     * <ol>
     *   <li>the clean-exit marker is present — finished at the timestamp it carries. Checked
     *   first and by presence alone, so it is immune to clock skew between the producing host
     *   and the hub: the producer-written timestamp is recorded as the finish time, never
     *   compared against the hub clock;</li>
     *   <li>the heartbeat has gone stale — finished at the last heartbeat, which is when the
     *   JVM was last known alive. This is the crash path, where nothing closed the library;</li>
     *   <li>a liveness file could not be read — left alone. A file that is there and unreadable
     *   says nothing about whether the JVM is running, and any timestamp would be a fabrication
     *   rather than a reading. The next sweep looks again, and an instance whose volume never
     *   recovers is closed by its next session instead;</li>
     *   <li>no liveness file, and the hub saw the session less than the startup grace ago — left
     *   alone: the JVM may still be starting;</li>
     *   <li>no liveness file past the startup grace, and the session directory holds no visible
     *   file — finished at {@code createdAt}. Nothing ever ran there: the profiler opens its
     *   recording and the agent writes its first beat the moment the JVM starts. This is the
     *   process that died before it got that far, typically the last restart of a crash-looping
     *   pod that a rollout then deleted: no next session will ever arrive for it, and without this
     *   it would keep itself and its instance ACTIVE for good. {@code createdAt} rather than
     *   {@code originCreatedAt}, because the timeline starts the session's bar there and a
     *   producer-clock timestamp could fall before it;</li>
     *   <li>no liveness file past the startup grace, and the directory holds data or could not be
     *   listed — left alone. Something recorded without reporting liveness, so the session may
     *   still be live and is closed by the instance's next session; a directory that cannot be
     *   listed proves nothing either way.</li>
     * </ol>
     *
     * <p>The order of 3 against 4–6 matters: a mount that answers an ordinary {@code IOException}
     * is worth a warning and must never reach the empty-session finish, an application without the
     * library is not worth a warning.</p>
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

        SessionContentRead content = sessionContentReader.read(sessionPath);

        // Case 5: silent and empty past the grace — nothing ever ran in this session
        if (content instanceof SessionContentRead.Empty) {
            LOG.info("Session never reported liveness nor recorded a file, marking finished at its start: "
                            + "session_id={} created_at={}",
                    sessionInfo.sessionId(), sessionInfo.createdAt());
            markFinished(ref, sessionInfo.createdAt());
            return true;
        }

        // Case 6: something recorded without reporting liveness, or the directory could not be
        // listed. It is closed by the instance's next session instead.
        LOG.trace("No liveness reported, session left for the instance's next session: session_id={} content={}",
                sessionInfo.sessionId(), content);
        return false;
    }
}
