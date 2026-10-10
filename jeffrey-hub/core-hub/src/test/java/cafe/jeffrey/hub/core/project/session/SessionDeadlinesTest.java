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

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class SessionDeadlinesTest {

    private static final Duration HEARTBEAT_THRESHOLD = Duration.ofSeconds(10);
    private static final Duration STARTUP_GRACE = Duration.ofMinutes(1);

    @Test
    void acceptsPositiveDurations() {
        var deadlines = new SessionDeadlines(HEARTBEAT_THRESHOLD, STARTUP_GRACE);

        assertEquals(HEARTBEAT_THRESHOLD, deadlines.heartbeatThreshold());
        assertEquals(STARTUP_GRACE, deadlines.startupGrace());
    }

    @Test
    void rejectsAMissingDuration() {
        assertThrows(IllegalArgumentException.class, () -> new SessionDeadlines(null, STARTUP_GRACE));
        assertThrows(IllegalArgumentException.class, () -> new SessionDeadlines(HEARTBEAT_THRESHOLD, null));
    }

    @Test
    void rejectsAZeroOrNegativeDuration() {
        assertThrows(IllegalArgumentException.class, () -> new SessionDeadlines(Duration.ZERO, STARTUP_GRACE));
        assertThrows(IllegalArgumentException.class,
                () -> new SessionDeadlines(HEARTBEAT_THRESHOLD, Duration.ofSeconds(-1)));
    }
}
