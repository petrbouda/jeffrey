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

package cafe.jeffrey.shared.volume;

import cafe.jeffrey.shared.volume.liveness.HeartbeatReader;
import cafe.jeffrey.shared.volume.liveness.SessionDeadlines;
import cafe.jeffrey.shared.volume.liveness.SessionLiveness;

import java.time.Clock;
import java.time.Duration;

/**
 * How the layout's tests read a session's liveness; the volume itself is written by
 * {@code VolumeWriter}, as the provisioner and the heartbeat library write it.
 */
public final class VolumeFixture {

    public static final Duration HEARTBEAT_THRESHOLD = Duration.ofSeconds(10);
    public static final Duration STARTUP_GRACE = Duration.ofMinutes(1);

    private VolumeFixture() {
    }

    public static SessionLiveness liveness(Clock clock, VolumeAccess volume) {
        return new SessionLiveness(
                clock, new HeartbeatReader(volume), new SessionDeadlines(HEARTBEAT_THRESHOLD, STARTUP_GRACE));
    }
}
