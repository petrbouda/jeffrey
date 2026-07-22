/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package cafe.jeffrey.shared.common;

import java.time.Duration;

/**
 * Shared constants for the file-based heartbeat mechanism.
 * Used by both the CLI (producer wiring) and the platform (consumer).
 *
 * <p>The agent module duplicates the relevant subset of these constants
 * because it must remain zero-dependency for minimal JAR size.</p>
 */
public abstract class HeartbeatConstants {

    /** Directory name inside a session directory that holds the heartbeat file */
    public static final String HEARTBEAT_DIR = ".heartbeat";

    /** Name of the heartbeat file (contains epoch millis) */
    public static final String HEARTBEAT_FILE = "heartbeat";

    /**
     * Name of the clean-exit marker file (contains epoch millis). Written into
     * {@link #HEARTBEAT_DIR} by the agent's shutdown hook; its presence lets the
     * hub finish a session deterministically instead of waiting for the
     * heartbeat to go stale. Absent after a hard crash (kill -9, OOM kill).
     */
    public static final String FINISHED_FILE = "finished";

    /** Agent argument key for heartbeat directory path */
    public static final String PARAM_DIR = "heartbeat.dir";

    /** Agent argument key for heartbeat interval in milliseconds */
    public static final String PARAM_INTERVAL = "heartbeat.interval";

    /** Agent argument key to enable/disable heartbeat (optional, defaults to true) */
    public static final String PARAM_ENABLED = "heartbeat.enabled";

    /** Default heartbeat interval. Must match {@code AgentArgs.DEFAULT_INTERVAL} in jeffrey-agent. */
    public static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(5);
}
