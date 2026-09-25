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

package cafe.jeffrey.heartbeat;

import java.time.Duration;

/**
 * The on-disk contract between a profiled JVM and a Jeffrey Hub: a directory holding a periodically
 * rewritten {@code heartbeat} file and, after a clean exit, a {@code finished} marker. Both contain
 * epoch millis as plain text.
 *
 * <p><b>This is the second copy of these values, and deliberately so.</b> The other is
 * {@code cafe.jeffrey.shared.common.HeartbeatConstants}, which the hub reads them with, and it
 * lives in a module no profiled application should ever be made to pull in. This one is an
 * ordinary dependency an application compiles against, so it must bring nothing with it.</p>
 *
 * <p>The values are three strings and a duration and have not changed since they were introduced.
 * If they ever do, both copies move together.</p>
 */
public final class HeartbeatFiles {

    /** Directory, inside the session directory, that holds the liveness files. */
    public static final String DIRECTORY = ".heartbeat";

    /** Periodically rewritten; its content is when the JVM was last known alive. */
    public static final String HEARTBEAT_FILE = "heartbeat";

    /**
     * Written once on clean shutdown. Its presence lets the hub finish a session immediately
     * instead of waiting for the heartbeat to go stale, and it is absent after a hard kill.
     */
    public static final String FINISHED_FILE = "finished";

    /**
     * How often the heartbeat is rewritten. Must match {@code HeartbeatConstants.DEFAULT_INTERVAL}:
     * the hub's staleness threshold is chosen as a multiple of it, so a producer that beats more
     * slowly than the hub expects reads as dead while it is running.
     */
    public static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(5);

    private HeartbeatFiles() {
    }
}
