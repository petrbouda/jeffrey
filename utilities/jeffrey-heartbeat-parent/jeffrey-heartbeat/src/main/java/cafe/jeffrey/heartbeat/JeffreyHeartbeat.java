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

import cafe.jeffrey.heartbeat.core.HeartbeatLog;
import cafe.jeffrey.heartbeat.core.HeartbeatLoop;

import java.time.Clock;

/**
 * Reports to a Jeffrey Hub that this JVM is alive, and tells it when the JVM stopped.
 *
 * <p>It is on by default: carrying the dependency is what opts an application in. Only the
 * application can switch it off, through {@link HeartbeatSettings#ENABLED_PROPERTY} (or
 * {@link HeartbeatSettings#ENABLED_ENV}). The hub holds a session to the heartbeat deadline once
 * its first liveness file appears, so an application that never reports is never finished for
 * staying silent.</p>
 *
 * <p>A provisioned JVM usually does not need it: the Provisioner attaches the Jeffrey agent, which
 * writes the same files, unless a deployment sets {@code jeffrey-agent.enabled = false}. When the
 * agent is beating it sets {@link HeartbeatSettings#AGENT_ACTIVE_PROPERTY} and this library stays
 * inert, so an application may carry the dependency either way and still have one writer.</p>
 *
 * <p>Typical use in a provisioned application is a single call at startup:</p>
 *
 * <pre>{@code
 * JeffreyHeartbeat.startFromEnvironment();
 * }</pre>
 *
 * <p>which reads what the Provisioner exported, begins beating, and writes the clean-exit marker
 * from a JVM shutdown hook. A container that manages its own lifecycle — Spring Boot, through
 * {@code jeffrey-heartbeat-spring-boot-starter} — calls {@link #start(HeartbeatSettings)} instead
 * and closes the instance itself, so the marker is written when the context shuts down rather than
 * when the JVM does.</p>
 *
 * <p><b>Nothing here fails an application.</b> A missing directory, an unreadable setting, a full
 * disk: each is logged once and leaves an inert instance behind. Liveness reporting is Jeffrey's
 * concern, and an application that cannot report it should still run.</p>
 */
public final class JeffreyHeartbeat implements AutoCloseable {

    private static final HeartbeatLog LOG = new Slf4jHeartbeatLog(JeffreyHeartbeat.class);

    private static final String SHUTDOWN_THREAD_NAME = "jeffrey-heartbeat-shutdown";

    /** The running loop, or {@code null} for an inert instance. */
    private final HeartbeatLoop loop;

    private JeffreyHeartbeat(HeartbeatLoop loop) {
        this.loop = loop;
    }

    /**
     * Starts beating with settings read from the environment and registers a JVM shutdown hook to
     * write the clean-exit marker. The call for an application with no lifecycle of its own.
     *
     * @return the running instance, so a caller that does have a lifecycle can still close it early
     */
    public static JeffreyHeartbeat startFromEnvironment() {
        JeffreyHeartbeat heartbeat = start(HeartbeatSettings.fromEnvironment());
        Runtime.getRuntime().addShutdownHook(new Thread(heartbeat::close, SHUTDOWN_THREAD_NAME));
        return heartbeat;
    }

    /**
     * Starts beating with the given settings. The caller owns the returned instance and is
     * responsible for closing it, which is what writes the clean-exit marker.
     *
     * @return a running instance, or an inert one when the settings name nowhere to write, say not
     * to, name a directory that cannot be created, or the Jeffrey agent already reports — never
     * {@code null}
     */
    public static JeffreyHeartbeat start(HeartbeatSettings settings) {
        return start(settings, Clock.systemUTC());
    }

    /** As {@link #start(HeartbeatSettings)}, with the clock the timestamps come from. */
    public static JeffreyHeartbeat start(HeartbeatSettings settings, Clock clock) {
        if (Boolean.getBoolean(HeartbeatSettings.AGENT_ACTIVE_PROPERTY)) {
            LOG.info("Jeffrey heartbeat not started, the Jeffrey agent already reports liveness: property="
                    + HeartbeatSettings.AGENT_ACTIVE_PROPERTY);
            return new JeffreyHeartbeat(null);
        }
        if (!settings.writable()) {
            LOG.debug("Jeffrey heartbeat not started: enabled=" + settings.enabled()
                    + " directory=" + settings.directory(), null);
            return new JeffreyHeartbeat(null);
        }
        return new JeffreyHeartbeat(
                HeartbeatLoop.start(settings.directory(), settings.interval(), clock, LOG).orElse(null));
    }

    /** Whether this instance is actually reporting — false once it is inert or closed. */
    public boolean running() {
        return loop != null && loop.running();
    }

    /**
     * Stops beating and writes the clean-exit marker, which is what lets the hub finish the session
     * at once instead of waiting for the heartbeat to go stale. Closing twice, or closing an inert
     * instance, is a no-op.
     */
    @Override
    public void close() {
        if (loop != null) {
            loop.close();
        }
    }
}
