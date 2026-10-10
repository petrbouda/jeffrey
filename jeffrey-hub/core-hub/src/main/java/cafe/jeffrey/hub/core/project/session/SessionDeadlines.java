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
 * @param startupGrace       how long after the hub first saw it a session may stay without a liveness
 *                           file before it is finished as having sent no heartbeat
 */
public record SessionDeadlines(Duration heartbeatThreshold, Duration startupGrace) {

    public SessionDeadlines {
        requirePositive(heartbeatThreshold, "heartbeatThreshold");
        requirePositive(startupGrace, "startupGrace");
    }

    private static void requirePositive(Duration duration, String name) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException(name + " must be positive: " + duration);
        }
    }
}
