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

package cafe.jeffrey.heartbeat;

import java.time.Duration;

/**
 * The on-disk contract between a profiled JVM and a Jeffrey Hub: a directory holding a periodically
 * rewritten {@code heartbeat} file and, after a clean exit, a {@code finished} marker. Both contain
 * epoch millis as plain text.
 *
 * <p><b>This is the third copy of these values, and deliberately so.</b> The other two are
 * {@code cafe.jeffrey.shared.common.HeartbeatConstants}, which the hub reads them with, and a
 * private copy inside {@code jeffrey-agent}. None of the three may depend on another: the hub's
 * lives in a module no application should ever pull in, the agent's jar is appended to the
 * <em>system</em> class path of every JVM it attaches to — so anything it bundled would win
 * parent-first delegation over the application's own copy — and this one is an ordinary dependency
 * an application compiles against. A shared artifact would put the agent's copy and this one on the
 * same JVM at different versions, which is the exact collision the agent shed when it dropped its
 * bytecode engine.</p>
 *
 * <p>The values are four strings and a duration and have not changed since they were introduced.
 * If they ever do, all three copies move together, and {@code HeartbeatFilesContractTest} in the
 * hub is what fails when they do not.</p>
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
     * How often the heartbeat is rewritten. Must match {@code HeartbeatConstants.DEFAULT_INTERVAL}
     * and the agent's own default: the hub's staleness threshold is chosen as a multiple of it, so
     * a producer that beats more slowly than the hub expects reads as dead while it is running.
     */
    public static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(5);

    private HeartbeatFiles() {
    }
}
