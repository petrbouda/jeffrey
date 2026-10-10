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

import java.time.Duration;

/**
 * The two deadlines the session-finished detector holds an unfinished session to.
 *
 * @param heartbeatThreshold how long after its last heartbeat a reporting session is taken as ended
 * @param startupGrace       how long after the hub first saw it a session may stay silent and empty
 *                           — no liveness file, no recorded file — before it is taken as never having
 *                           started
 */
public record SessionDeadlines(Duration heartbeatThreshold, Duration startupGrace) {

    public SessionDeadlines {
        if (heartbeatThreshold == null || heartbeatThreshold.isNegative() || heartbeatThreshold.isZero()) {
            throw new IllegalArgumentException("heartbeatThreshold must be positive: " + heartbeatThreshold);
        }
        if (startupGrace == null || startupGrace.isNegative() || startupGrace.isZero()) {
            throw new IllegalArgumentException("startupGrace must be positive: " + startupGrace);
        }
    }
}
