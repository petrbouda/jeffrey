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

package cafe.jeffrey.shared.common;

import java.time.Duration;

/**
 * Shared constants for the file-based heartbeat mechanism, as the hub reads it.
 *
 * <p>{@code utilities/jeffrey-heartbeat-parent/jeffrey-heartbeat} — the library that writes these files from inside a
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
     * {@link #HEARTBEAT_DIR} when the library is closed; its presence lets the
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

    /**
     * System property naming the directory the liveness files go in. The Provisioner writes it
     * into the argfile, which is the one channel every deployment path delivers: the {@code .env}
     * file is written only when a deployment asks for one, and the container entrypoint execs the
     * JVM with the argfile without sourcing a shell file at all.
     *
     * <p>Must match {@code HeartbeatSettings.DIRECTORY_PROPERTY} in {@code jeffrey-heartbeat},
     * which resolves it ahead of {@code JEFFREY_HEARTBEAT_DIR}.</p>
     */
    public static final String DIRECTORY_PROPERTY = "jeffrey.heartbeat.dir";

    /**
     * System property declaring whether anything will report liveness at all. Carried the same way
     * and for the same reason as {@link #DIRECTORY_PROPERTY}, and must match
     * {@code HeartbeatSettings.ENABLED_PROPERTY}.
     */
    public static final String ENABLED_PROPERTY = "jeffrey.heartbeat.enabled";
}
