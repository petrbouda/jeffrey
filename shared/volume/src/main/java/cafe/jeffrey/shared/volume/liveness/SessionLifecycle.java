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

import java.time.Instant;

/**
 * Whether a session is still recording, as its liveness files say at the moment of reading.
 *
 * @param finishedAt       when it finished; {@code null} while it is still recording
 * @param heartbeatMissing it was finished at its start because no liveness file appeared within
 *                         the startup grace
 */
public record SessionLifecycle(Instant finishedAt, boolean heartbeatMissing) {

    private static final SessionLifecycle RECORDING = new SessionLifecycle(null, false);

    public SessionLifecycle {
        if (heartbeatMissing && finishedAt == null) {
            throw new IllegalArgumentException("A session missing its heartbeat is finished, so it needs a finish time");
        }
    }

    public static SessionLifecycle recording() {
        return RECORDING;
    }

    public static SessionLifecycle finished(Instant finishedAt) {
        return new SessionLifecycle(finishedAt, false);
    }

    public static SessionLifecycle finishedWithoutHeartbeat(Instant createdAt) {
        return new SessionLifecycle(createdAt, true);
    }

    public boolean isRecording() {
        return finishedAt == null;
    }
}
