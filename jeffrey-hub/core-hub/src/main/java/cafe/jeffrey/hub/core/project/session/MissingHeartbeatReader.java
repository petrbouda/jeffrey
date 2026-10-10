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

package cafe.jeffrey.hub.core.project.session;

import cafe.jeffrey.hub.model.repository.MissingHeartbeat;
import cafe.jeffrey.shared.common.HeartbeatConstants;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads why a session ended without a heartbeat: whether the Jeffrey Agent was written into its
 * directory. One stat, and only for a session the detector finished for sending no heartbeat — the
 * rest carry nothing.
 */
public class MissingHeartbeatReader {

    /**
     * @param heartbeatMissing whether the session ended because no heartbeat ever arrived
     * @param sessionPath      the session directory
     * @return why the heartbeat was missing, or {@code null} for a session that sent one
     */
    public MissingHeartbeat read(boolean heartbeatMissing, Path sessionPath) {
        if (!heartbeatMissing) {
            return null;
        }
        return new MissingHeartbeat(Files.exists(sessionPath.resolve(HeartbeatConstants.AGENT_JAR_FILE)));
    }
}
