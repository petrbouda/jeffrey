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
package cafe.jeffrey.agent;

import cafe.jeffrey.heartbeat.core.HeartbeatConfig;
import cafe.jeffrey.heartbeat.core.HeartbeatContract;
import cafe.jeffrey.heartbeat.core.HeartbeatLog;
import cafe.jeffrey.heartbeat.core.HeartbeatLoop;

import java.lang.instrument.Instrumentation;
import java.time.Clock;
import java.util.Optional;

/**
 * Entry point of the Jeffrey agent: reports this JVM's liveness to a Jeffrey Hub with the same
 * {@code jeffrey-heartbeat-core} loop the {@code jeffrey-heartbeat} library runs, shaded into this
 * jar under the agent's own package so it never meets the application's copy of the library.
 *
 * <p>Attached by the provisioner with {@code -javaagent} unless a deployment sets
 * {@code jeffrey-agent.enabled = false}, in which case an application that wants its liveness
 * reported carries {@code jeffrey-heartbeat} itself. Never both at once: once the agent is
 * beating it sets {@link HeartbeatContract#AGENT_ACTIVE_PROPERTY}, and the library stands down.
 *
 * <p>Every failure is logged and swallowed. An agent that throws from {@code premain} stops the
 * JVM, and an application must start even when its liveness cannot be reported.
 */
public final class JeffreyAgent {

    private static final HeartbeatLog LOG = new SystemLoggerHeartbeatLog(JeffreyAgent.class);

    private static final String SHUTDOWN_THREAD_NAME = "jeffrey-heartbeat-shutdown";

    private JeffreyAgent() {
    }

    public static void premain(String args, Instrumentation inst) {
        try {
            HeartbeatConfig config = HeartbeatConfig.resolve(System::getProperty, System::getenv, LOG);
            start(config, Clock.systemUTC()).ifPresent(loop ->
                    Runtime.getRuntime().addShutdownHook(new Thread(loop::close, SHUTDOWN_THREAD_NAME)));
        } catch (RuntimeException e) {
            LOG.warn("Jeffrey agent failed to start, liveness will not be reported", e);
        }
    }

    /**
     * Starts beating when the configuration allows it and marks the JVM as reported for by the agent.
     *
     * @return the running loop, or empty when nothing was started
     */
    static Optional<HeartbeatLoop> start(HeartbeatConfig config, Clock clock) {
        if (!config.writable()) {
            LOG.info("Jeffrey agent heartbeat not started: enabled=" + config.enabled()
                    + " directory=" + config.directory());
            return Optional.empty();
        }

        Optional<HeartbeatLoop> loop = HeartbeatLoop.start(config.directory(), config.interval(), clock, LOG);
        loop.ifPresent(ignored ->
                System.setProperty(HeartbeatContract.AGENT_ACTIVE_PROPERTY, Boolean.TRUE.toString()));
        return loop;
    }
}
