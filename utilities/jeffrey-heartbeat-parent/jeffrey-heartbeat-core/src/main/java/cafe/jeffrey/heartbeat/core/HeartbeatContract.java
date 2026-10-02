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
package cafe.jeffrey.heartbeat.core;

import java.time.Duration;

/**
 * The names a Jeffrey Hub and the writers of its liveness files agree on: the files, the settings
 * that place them, and the flag through which the Jeffrey agent tells the library to stand down.
 *
 * <p>The hub reads with its own copy in {@code HeartbeatConstants}, which no application should be
 * made to pull in; the two move together.
 */
public final class HeartbeatContract {

    /** Directory, inside the session directory, that holds the liveness files. */
    public static final String DIRECTORY = ".heartbeat";

    /** Periodically rewritten; its content is when the JVM was last known alive, in epoch millis. */
    public static final String HEARTBEAT_FILE = "heartbeat";

    /** Written once on clean shutdown, in epoch millis; absent after a hard kill. */
    public static final String FINISHED_FILE = "finished";

    /**
     * How often the heartbeat is rewritten. Must match {@code HeartbeatConstants.DEFAULT_INTERVAL}:
     * the hub's staleness threshold is a multiple of it.
     */
    public static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(5);

    private static final String PROPERTY_PREFIX = "jeffrey.heartbeat.";

    /** Names the heartbeat directory; the Provisioner writes it into the argfile. */
    public static final String DIRECTORY_PROPERTY = PROPERTY_PREFIX + "dir";
    public static final String DIRECTORY_ENV = "JEFFREY_HEARTBEAT_DIR";

    /** Milliseconds between heartbeats. */
    public static final String INTERVAL_PROPERTY = PROPERTY_PREFIX + "interval";
    public static final String INTERVAL_ENV = "JEFFREY_HEARTBEAT_INTERVAL";

    /** The application's own switch: {@code false} stands every writer down. */
    public static final String ENABLED_PROPERTY = PROPERTY_PREFIX + "enabled";
    public static final String ENABLED_ENV = "JEFFREY_HEARTBEAT_ENABLED";

    /** Names the session directory, whose {@link #DIRECTORY} is the same place; the fallback. */
    public static final String SESSION_PROPERTY = "jeffrey.current.session";
    public static final String SESSION_ENV = "JEFFREY_CURRENT_SESSION";

    /**
     * Set to {@code true} by the Jeffrey agent once it is beating. The library then stays inert,
     * because two writers in one JVM would share the same scratch files.
     */
    public static final String AGENT_ACTIVE_PROPERTY = PROPERTY_PREFIX + "agent";

    private HeartbeatContract() {
    }
}
