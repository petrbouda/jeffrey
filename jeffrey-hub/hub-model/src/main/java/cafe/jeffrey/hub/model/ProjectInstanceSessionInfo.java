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

package cafe.jeffrey.hub.model;

import java.nio.file.Path;
import java.time.Instant;

/**
 * One recording session as the hub knows it.
 *
 * @param heartbeatMissing the session ended because no heartbeat arrived within the startup grace —
 *                         set by the session-finished detector as it finishes the session
 */
public record ProjectInstanceSessionInfo(
        String sessionId,
        String repositoryId,
        String instanceId,
        int order,
        Path relativeSessionPath,
        Instant originCreatedAt,
        Instant createdAt,
        Instant finishedAt,
        boolean retained,
        boolean heartbeatMissing) {

    public ProjectInstanceSessionInfo {
        RelativePath.require(relativeSessionPath, "relativeSessionPath");
    }

    /**
     * A session that has not ended for a missing heartbeat — the state every session starts in.
     */
    public ProjectInstanceSessionInfo(
            String sessionId,
            String repositoryId,
            String instanceId,
            int order,
            Path relativeSessionPath,
            Instant originCreatedAt,
            Instant createdAt,
            Instant finishedAt,
            boolean retained) {

        this(sessionId, repositoryId, instanceId, order, relativeSessionPath,
                originCreatedAt, createdAt, finishedAt, retained, false);
    }

    /**
     * Creates a session that is not retained — the state every session starts in.
     */
    public static ProjectInstanceSessionInfo notRetained(
            String sessionId,
            String repositoryId,
            String instanceId,
            int order,
            Path relativeSessionPath,
            Instant originCreatedAt,
            Instant createdAt,
            Instant finishedAt) {

        return new ProjectInstanceSessionInfo(
                sessionId, repositoryId, instanceId, order,
                relativeSessionPath, originCreatedAt, createdAt, finishedAt, false);
    }
}
