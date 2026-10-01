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
import java.time.Duration;
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
     * one ended.</p>
     */
    public void forceFinish(SessionRef ref, Instant fallbackFinishedAt) {
        Instant finishedAt = fileHeartbeatReader.readFinishedMarker(ref.path()).timestamp()
                .or(() -> fileHeartbeatReader.readLastHeartbeat(ref.path()).timestamp())
                .orElse(fallbackFinishedAt);
        markFinished(ref, finishedAt);
    }

    /**
     * Applies the heartbeat deadline to one unfinished session and marks it finished when it
     * has stopped reporting. Used by the polling detector.
     *
     * <p>A session is held to this deadline only once it has shown that it reports liveness, by
     * writing a liveness file. The writer is the {@code jeffrey-heartbeat} library, an ordinary
     * dependency of the profiled application that is on unless the application switches it off,
     * so nothing outside the application declares whether it will report. A session that never
     * wrote a liveness file — no library, or one the application disabled — is not finished for
     * staying silent; it is closed when the instance's next session appears, by
     * {@link #forceFinish}.</p>
     *
     * <p>Four outcomes:</p>
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
     *   <li>no liveness file at all — left alone. Either the JVM is still starting up, or nothing
     *   in it reports; the two look the same from here, and finishing the second kind would end
     *   a session the profiler is still writing.</li>
     * </ol>
     *
     * <p>The order of 3 and 4 matters even though both leave the session alone: a mount that
     * answers an ordinary {@code IOException} is worth a warning, an application without the
     * library is not.</p>
     *
     * @return true if session was marked finished
     */
    public boolean tryFinishFromHeartbeat(SessionRef ref, Duration heartbeatThreshold) {
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
            Instant deadline = clock.instant().minus(heartbeatThreshold);
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

        // Case 4: nothing reports liveness for this session, at least not yet. It is closed by
        // the instance's next session instead.
        LOG.trace("No liveness reported, no heartbeat deadline applies: session_id={}",
                sessionInfo.sessionId());
        return false;
    }
}
