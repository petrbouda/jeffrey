/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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
