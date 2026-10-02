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

import java.lang.System.Logger.Level;
import java.lang.instrument.Instrumentation;
import java.time.Clock;
import java.util.Optional;

/**
 * Entry point of the Jeffrey agent: reports this JVM's liveness to a Jeffrey Hub by writing the
 * same heartbeat files the {@code jeffrey-heartbeat} library writes.
 *
 * <p>Attached by the provisioner with {@code -javaagent} unless a deployment sets
 * {@code jeffrey-agent.enabled = false}, in which case an application that wants its liveness
 * reported carries {@code jeffrey-heartbeat} itself. Never both at once: once the agent is
 * beating it sets {@link AgentSettings#AGENT_ACTIVE_PROPERTY}, and the library stands down when
 * it sees it, because two writers sharing one directory share its scratch files too.
 *
 * <p>Every failure is logged and swallowed. An agent that throws from {@code premain} stops the
 * JVM, and an application must start even when its liveness cannot be reported.
 */
public final class JeffreyAgent {

    private static final System.Logger LOG = System.getLogger(JeffreyAgent.class.getName());

    private static final String SHUTDOWN_THREAD_NAME = "jeffrey-heartbeat-shutdown";

    private JeffreyAgent() {
    }

    public static void premain(String args, Instrumentation inst) {
        try {
            start(AgentSettings.fromEnvironment(), Clock.systemUTC()).ifPresent(producer ->
                    Runtime.getRuntime().addShutdownHook(new Thread(producer::close, SHUTDOWN_THREAD_NAME)));
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Jeffrey agent failed to start, liveness will not be reported", e);
        }
    }

    /**
     * Starts beating when the settings allow it and marks the JVM as reported for by the agent.
     *
     * @return the running producer, or empty when nothing was started
     */
    static Optional<HeartbeatProducer> start(AgentSettings settings, Clock clock) {
        if (!settings.enabled()) {
            LOG.log(Level.INFO, "Jeffrey agent heartbeat is switched off by the application: property="
                    + AgentSettings.ENABLED_PROPERTY);
            return Optional.empty();
        }
        if (settings.directory() == null) {
            LOG.log(Level.INFO, "Jeffrey agent heartbeat has no directory configured: property="
                    + AgentSettings.DIRECTORY_PROPERTY);
            return Optional.empty();
        }

        Optional<HeartbeatProducer> producer = HeartbeatProducer.start(settings, clock);
        producer.ifPresent(ignored -> System.setProperty(AgentSettings.AGENT_ACTIVE_PROPERTY, Boolean.TRUE.toString()));
        return producer;
    }
}
