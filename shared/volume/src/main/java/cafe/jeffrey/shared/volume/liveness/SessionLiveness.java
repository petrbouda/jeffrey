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

package cafe.jeffrey.shared.volume.liveness;

import cafe.jeffrey.shared.volume.VolumePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;

/**
 * Decides whether a session is still recording from the files under its {@code .heartbeat/},
 * at the moment it is read. Nothing keeps a record of it: every listing asks again, with the
 * reader's clock, and compression, retention and downloads all act on the same answer.
 *
 * <p>The rules, in order:</p>
 * <ol>
 *   <li>a clean-exit {@code finished} marker — finished at the time it carries. Checked by
 *   presence first, so it is immune to clock skew between the producer and the reader;</li>
 *   <li>a newer session exists in the same instance — this one ended when that one started:
 *   finished at its last heartbeat, or at the newer session's start if it never beat;</li>
 *   <li>a heartbeat older than the threshold — finished at that heartbeat, the crash path;</li>
 *   <li>a liveness file that cannot be read — still recording: nothing is known, and a
 *   fabricated finish would let compression and retention act on a live chunk;</li>
 *   <li>no liveness file and the session younger than the startup grace — still recording, the
 *   JVM may be starting;</li>
 *   <li>no liveness file past the grace — finished at its start, as having sent no heartbeat.</li>
 * </ol>
 */
public class SessionLiveness {

    private static final Logger LOG = LoggerFactory.getLogger(SessionLiveness.class);

    private final Clock clock;
    private final HeartbeatReader reader;
    private final SessionDeadlines deadlines;

    public SessionLiveness(Clock clock, HeartbeatReader reader, SessionDeadlines deadlines) {
        this.clock = clock;
        this.reader = reader;
        this.deadlines = deadlines;
    }

    /**
     * @param sessionDir      the session's directory
     * @param createdAt       when the session started, from its marker
     * @param nextSessionStart when the instance's next session started; {@code null} for its newest
     */
    public SessionLifecycle assess(VolumePath sessionDir, Instant createdAt, Instant nextSessionStart) {
        LivenessRead finishedMarker = reader.readFinishedMarker(sessionDir);
        if (finishedMarker instanceof LivenessRead.Reported(Instant markerAt)) {
            return SessionLifecycle.finished(markerAt);
        }

        LivenessRead lastHeartbeat = reader.readLastHeartbeat(sessionDir);
        if (nextSessionStart != null) {
            return SessionLifecycle.finished(lastHeartbeat.timestamp().orElse(nextSessionStart));
        }

        if (lastHeartbeat instanceof LivenessRead.Reported(Instant heartbeatAt)) {
            Instant deadline = clock.instant().minus(deadlines.heartbeatThreshold());
            return heartbeatAt.isBefore(deadline)
                    ? SessionLifecycle.finished(heartbeatAt)
                    : SessionLifecycle.recording();
        }

        if (!finishedMarker.isAbsent() || !lastHeartbeat.isAbsent()) {
            LOG.debug("Liveness files unreadable, reporting the session as recording: session_dir={}", sessionDir);
            return SessionLifecycle.recording();
        }

        Instant startupDeadline = clock.instant().minus(deadlines.startupGrace());
        return createdAt.isAfter(startupDeadline)
                ? SessionLifecycle.recording()
                : SessionLifecycle.finishedWithoutHeartbeat(createdAt);
    }
}
