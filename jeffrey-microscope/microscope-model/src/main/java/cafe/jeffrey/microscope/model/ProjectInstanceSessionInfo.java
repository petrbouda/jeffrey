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

import java.nio.file.Path;
import java.time.Instant;

/**
 * One recording session as the hub knows it.
 *
 * <p>{@code heartbeatExpected} is what the session declared about its own liveness: {@code TRUE}
 * when the run was provisioned expecting the {@code jeffrey-heartbeat} library to report,
 * {@code FALSE} when it was not, and {@code null} when the session was declared by a provisioner
 * too old to say. Only a {@code TRUE} session is held to the heartbeat deadline — see
 * {@code SessionFinisher}.
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
        Boolean heartbeatExpected) {

    /**
     * Whether this session promised to report liveness, and may therefore be finished for
     * failing to. An undeclared session ({@code null}) is not held to that promise.
     */
    public boolean expectsHeartbeat() {
        return Boolean.TRUE.equals(heartbeatExpected);
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
                relativeSessionPath, originCreatedAt, createdAt, finishedAt, false, false, null);
    }

    /**
     * Copy of this session info with the derived failed flag set.
     */
    public ProjectInstanceSessionInfo withFailed(boolean failed) {
        return new ProjectInstanceSessionInfo(
                sessionId, repositoryId, instanceId, order,
                relativeSessionPath, originCreatedAt, createdAt, finishedAt, retained, failed, heartbeatExpected);
    }

    /**
     * Copy of this session info carrying what the session declared about reporting liveness.
     */
    public ProjectInstanceSessionInfo withHeartbeatExpected(Boolean heartbeatExpected) {
        return new ProjectInstanceSessionInfo(
                sessionId, repositoryId, instanceId, order,
                relativeSessionPath, originCreatedAt, createdAt, finishedAt, retained, failed, heartbeatExpected);
    }
}
