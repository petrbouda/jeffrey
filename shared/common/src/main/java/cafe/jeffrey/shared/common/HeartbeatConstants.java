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
 * Shared constants for the file-based heartbeat mechanism, as the hub reads it.
 *
 * <p>{@code utilities/jeffrey-heartbeat} — the library that writes these files from inside a
 * profiled application — carries its own copy in {@code HeartbeatFiles} rather than depending on
 * this module, which no application should ever be made to pull in. The two move together; there
 * is no third reader.</p>
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

    /**
     * Default heartbeat interval. Must match {@code HeartbeatFiles.DEFAULT_INTERVAL} in
     * {@code jeffrey-heartbeat}: the hub's staleness threshold is chosen as a multiple of it, so a
     * producer beating more slowly than the hub expects reads as dead while it is running.
     */
    public static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(5);
}
