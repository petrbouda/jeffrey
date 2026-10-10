/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.microscope.model;

import cafe.jeffrey.microscope.model.repository.SessionHeartbeat;

import java.nio.file.Path;
import java.time.Instant;

/**
 * One recording session as the hub knows it.
 *
 * @param heartbeat  the session's liveness as the hub reports it
 * @param lastFileAt when the session last wrote a file, or {@code null} when it wrote none
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
        boolean failed,
        SessionHeartbeat heartbeat,
        Instant lastFileAt) {

    public ProjectInstanceSessionInfo {
        heartbeat = heartbeat == null ? SessionHeartbeat.UNKNOWN : heartbeat;
    }

    /**
     * Creates a session that is not retained — the state every session starts in.
     * The failed flag starts false: it is a derived property (finished with zero bytes
     * on disk), computed against repository storage and populated only when session
     * info is reconstructed from a hub response — persisted rows always carry false.
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
                relativeSessionPath, originCreatedAt, createdAt, finishedAt, false, false,
                SessionHeartbeat.UNKNOWN, null);
    }

    /**
     * Copy of this session info with the derived failed flag set.
     */
    public ProjectInstanceSessionInfo withFailed(boolean failed) {
        return new ProjectInstanceSessionInfo(
                sessionId, repositoryId, instanceId, order,
                relativeSessionPath, originCreatedAt, createdAt, finishedAt, retained, failed,
                heartbeat, lastFileAt);
    }

    /**
     * Copy of this session info with what the hub read from the session directory: its liveness
     * and when it last wrote a file.
     */
    public ProjectInstanceSessionInfo withVolumeFacts(SessionHeartbeat heartbeat, Instant lastFileAt) {
        return new ProjectInstanceSessionInfo(
                sessionId, repositoryId, instanceId, order,
                relativeSessionPath, originCreatedAt, createdAt, finishedAt, retained, failed,
                heartbeat, lastFileAt);
    }

    /**
     * Live: not finished, and reporting liveness — a session whose heartbeat is missing is not.
     */
    public boolean isActive() {
        return finishedAt == null && !heartbeat.missing();
    }
}
