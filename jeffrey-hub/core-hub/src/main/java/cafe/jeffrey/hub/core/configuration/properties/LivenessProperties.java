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

package cafe.jeffrey.hub.core.configuration.properties;

import cafe.jeffrey.hub.core.volume.SessionDeadlines;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code jeffrey.hub.liveness.*}: how a session's heartbeat files are read into whether it is
 * still recording. Bound by Boot, which is why it is a mutable bean.
 */
@ConfigurationProperties("jeffrey.hub.liveness")
public class LivenessProperties {

    private static final Duration DEFAULT_HEARTBEAT_THRESHOLD = Duration.ofSeconds(10);
    private static final Duration DEFAULT_STARTUP_GRACE = Duration.ofMinutes(1);

    /** How long after its last heartbeat a session is taken as ended. */
    private Duration heartbeatThreshold = DEFAULT_HEARTBEAT_THRESHOLD;

    /** How long a session may go without any liveness file before it is finished at its start. */
    private Duration startupGrace = DEFAULT_STARTUP_GRACE;

    public Duration getHeartbeatThreshold() {
        return heartbeatThreshold;
    }

    public void setHeartbeatThreshold(Duration heartbeatThreshold) {
        this.heartbeatThreshold = heartbeatThreshold;
    }

    public Duration getStartupGrace() {
        return startupGrace;
    }

    public void setStartupGrace(Duration startupGrace) {
        this.startupGrace = startupGrace;
    }

    public SessionDeadlines deadlines() {
        return new SessionDeadlines(heartbeatThreshold, startupGrace);
    }
}
