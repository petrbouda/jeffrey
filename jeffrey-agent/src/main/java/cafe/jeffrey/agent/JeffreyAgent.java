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

package cafe.jeffrey.agent;

import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;

public class JeffreyAgent {

    private static final System.Logger LOG = System.getLogger(JeffreyAgent.class.getName());

    /**
     * Declared without the {@code Instrumentation} parameter on purpose. The JVM accepts either
     * form and prefers the two-argument one, so taking the single-argument form is how this agent
     * states that it transforms no bytecode: it writes a liveness file and emits one JFR event.
     * Anything that needs to rewrite a class belongs in a library the application depends on.
     */
    public static void premain(String args) {
        AgentArgs agentArgs = AgentArgs.parse(args);

        startHeartbeat(agentArgs);
        startAppInformation(agentArgs);
    }

    // Heartbeat writes a liveness file; AppInformation emits a JFR event. They are
    // independent concerns and either can run without the other.
    private static void startHeartbeat(AgentArgs agentArgs) {
        Path heartbeatDir = agentArgs.heartbeatDir();
        if (!agentArgs.heartbeatEnabled() || heartbeatDir == null) {
            LOG.log(Level.INFO, "Heartbeat is disabled or no heartbeat directory configured");
            return;
        }

        if (!Files.isDirectory(heartbeatDir)) {
            LOG.log(Level.WARNING, "Heartbeat directory does not exist: " + heartbeatDir);
            return;
        }

        HeartbeatProducer producer = new HeartbeatProducer(heartbeatDir, agentArgs.heartbeatInterval());
        Runtime.getRuntime().addShutdownHook(new Thread(producer::close, "jeffrey-heartbeat-shutdown"));
        producer.start();

        LOG.log(Level.INFO, "Heartbeat started: dir=" + heartbeatDir + " interval=" + agentArgs.heartbeatInterval());
    }

    private static void startAppInformation(AgentArgs agentArgs) {
        AppInformation appInfo = agentArgs.appInfo();
        if (appInfo == null) {
            LOG.log(Level.INFO, "Application information is not configured, skipping jeffrey.AppInformation emission");
            return;
        }

        AppInformationEmitter.start(appInfo);
        LOG.log(Level.INFO, "Application information emitter started: sessionId=" + appInfo.sessionId());
    }
}
