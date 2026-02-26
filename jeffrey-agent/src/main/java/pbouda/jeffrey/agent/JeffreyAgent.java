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

package pbouda.jeffrey.agent;

import java.lang.System.Logger.Level;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;

public class JeffreyAgent {

    private static final System.Logger LOG = System.getLogger(JeffreyAgent.class.getName());

    public static void premain(String args, Instrumentation inst) {
        AgentArgs agentArgs = AgentArgs.parse(args);

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
}
