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

import cafe.jeffrey.hub.model.repository.SessionHeartbeat;
import cafe.jeffrey.shared.common.HeartbeatConstants;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Reads what a listing shows about a session's liveness: whether its heartbeat is missing, which
 * the session-finished detector decided and the row carries, the last heartbeat of a session still
 * open, and — only for a session whose heartbeat is missing — whether the Jeffrey Agent was written
 * into its directory, which is what tells a switched-off agent from a JVM that never ran it.
 *
 * <p>One small read per open session and one stat per silent one, never a walk: the directory
 * listing the caller already made answers everything else.</p>
 */
public class SessionHeartbeatReader {

    private final FileHeartbeatReader fileHeartbeatReader;

    public SessionHeartbeatReader(FileHeartbeatReader fileHeartbeatReader) {
        this.fileHeartbeatReader = fileHeartbeatReader;
    }

    /**
     * What is known without touching the volume: the flag the row carries.
     */
    public static SessionHeartbeat fromRow(boolean heartbeatMissing) {
        return new SessionHeartbeat(heartbeatMissing, null, false);
    }

    /**
     * @param heartbeatMissing whether the detector found no heartbeat within the startup grace
     * @param finished         whether the session is finished, which leaves no heartbeat worth showing
     * @param sessionPath      the session directory
     */
    public SessionHeartbeat read(boolean heartbeatMissing, boolean finished, Path sessionPath) {
        Instant lastHeartbeatAt = finished
                ? null
                : fileHeartbeatReader.readLastHeartbeat(sessionPath).timestamp().orElse(null);
        boolean agentPresent = heartbeatMissing
                && Files.exists(sessionPath.resolve(HeartbeatConstants.AGENT_JAR_FILE));
        return new SessionHeartbeat(heartbeatMissing, lastHeartbeatAt, agentPresent);
    }
}
