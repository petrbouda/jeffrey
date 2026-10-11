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

package cafe.jeffrey.provisioner;

import cafe.jeffrey.provisioner.config.ConfigPaths;
import cafe.jeffrey.provisioner.feature.JvmFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;

/**
 * Decides whether a session's JVM runs with the Jeffrey agent, and from which jar.
 *
 * <p>The agent is located exactly like async-profiler (see {@link AsyncProfilerResolver}):
 * {@code jeffrey-agent.path} ({@code JEFFREY_AGENT_PATH}) names the jar, jeffrey-jib bakes it into
 * the image under {@code /opt/jeffrey} and sets that variable, and a deployment outside jeffrey-jib
 * points the same setting at the jar it ships. The JVM loads it from there; nothing is copied into
 * the session.
 *
 * <p>Fails open like the profiler does: when the jar is needed and it is not there, the session
 * runs without the agent and a warning says so, rather than handing the JVM a {@code -javaagent}
 * it cannot load, which would stop the application from starting.
 */
public class JeffreyAgentResolver {

    private static final Logger LOG = LoggerFactory.getLogger(JeffreyAgentResolver.class);

    private final Predicate<Path> jarAvailable;

    public JeffreyAgentResolver() {
        this(Files::isRegularFile);
    }

    JeffreyAgentResolver(Predicate<Path> jarAvailable) {
        this.jarAvailable = jarAvailable;
    }

    /**
     * @param enabled   whether {@code jeffrey-agent.enabled} switches the agent on
     * @param agentPath the jar {@code jeffrey-agent.path} names, or {@code null} when nothing names one
     */
    public JvmFeature.JeffreyAgent resolve(boolean enabled, String agentPath) {
        if (!enabled) {
            LOG.info("Jeffrey agent DISABLED, liveness is reported only if the application carries jeffrey-heartbeat");
            return JvmFeature.JeffreyAgent.disabled();
        }
        if (agentPath == null || agentPath.isBlank()) {
            LOG.warn("No Jeffrey agent jar is configured, the session runs without it: setting={}",
                    ConfigPaths.JEFFREY_AGENT_PATH);
            return JvmFeature.JeffreyAgent.disabled();
        }
        Path jar = Path.of(agentPath);
        if (!jarAvailable.test(jar)) {
            LOG.warn("Jeffrey agent jar not found, the session runs without it: agent_path={}", jar);
            return JvmFeature.JeffreyAgent.disabled();
        }
        return new JvmFeature.JeffreyAgent(jar);
    }
}
