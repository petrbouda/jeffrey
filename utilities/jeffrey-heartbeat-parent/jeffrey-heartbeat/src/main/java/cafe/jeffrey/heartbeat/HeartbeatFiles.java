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

import cafe.jeffrey.heartbeat.core.HeartbeatContract;

import java.time.Duration;

/**
 * The on-disk contract between a profiled JVM and a Jeffrey Hub: a directory holding a periodically
 * rewritten {@code heartbeat} file and, after a clean exit, a {@code finished} marker. Both contain
 * epoch millis as plain text. The values are {@link HeartbeatContract}'s, shared with the Jeffrey
 * agent; they are repeated here only so this library's public API keeps naming them.
 */
public final class HeartbeatFiles {

    /** Directory, inside the session directory, that holds the liveness files. */
    public static final String DIRECTORY = HeartbeatContract.DIRECTORY;

    /** Periodically rewritten; its content is when the JVM was last known alive. */
    public static final String HEARTBEAT_FILE = HeartbeatContract.HEARTBEAT_FILE;

    /**
     * Written once on clean shutdown. Its presence lets the hub finish a session immediately
     * instead of waiting for the heartbeat to go stale, and it is absent after a hard kill.
     */
    public static final String FINISHED_FILE = HeartbeatContract.FINISHED_FILE;

    /** How often the heartbeat is rewritten; the hub's staleness threshold is a multiple of it. */
    public static final Duration DEFAULT_INTERVAL = HeartbeatContract.DEFAULT_INTERVAL;

    private HeartbeatFiles() {
    }
}
