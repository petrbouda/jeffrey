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

package cafe.jeffrey.hub.model.repository;

import java.time.Instant;

/**
 * What a session's liveness reporting looks like from the hub.
 *
 * @param missing         no heartbeat arrived within the startup grace: heartbeats are not configured
 *                        or not emitted, and the session is not taken as live
 * @param lastHeartbeatAt the last heartbeat of a session still open, or {@code null} when there is
 *                        none or the session is finished
 * @param agentPresent    whether the Jeffrey Agent jar was written into the session directory; read
 *                        only for a session whose heartbeat is missing, where it tells a switched-off
 *                        agent from a JVM that never got as far as running it
 */
public record SessionHeartbeat(boolean missing, Instant lastHeartbeatAt, boolean agentPresent) {

    /** Nothing known about the session's liveness: the state of a session loaded without its files. */
    public static final SessionHeartbeat UNKNOWN = new SessionHeartbeat(false, null, false);
}
