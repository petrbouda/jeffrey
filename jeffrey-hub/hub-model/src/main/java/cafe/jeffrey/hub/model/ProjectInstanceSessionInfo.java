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

package cafe.jeffrey.hub.model;

import cafe.jeffrey.shared.common.config.ConfigSource;
import cafe.jeffrey.shared.common.model.repository.AppliedConfigLayer;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * One recording session as the hub knows it.
 *
 * <p>{@code heartbeatExpected} is what the session declared about its own liveness: {@code TRUE}
 * when the run was provisioned expecting the {@code jeffrey-heartbeat} library to report,
 * {@code FALSE} when it was not, and {@code null} when the session was declared by a provisioner
 * too old to say. Only a {@code TRUE} session is held to the heartbeat deadline — see
 * {@code SessionFinisher}.
 *
 * <p>The profiler fields are what the run was actually started with: the resolved command, the
 * configuration layer its base came from, and the hub-published files that were merged with the
 * digest each had when it was read. They are copied from the session's marker and never computed
 * here, so they describe the run rather than what the hub holds now — which is exactly what makes
 * comparing them with the current configuration meaningful.</p>
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
        Boolean heartbeatExpected,
        ConfigSource profilerCommandSource,
        String profilerCommand,
        List<AppliedConfigLayer> configLayers) {

    public ProjectInstanceSessionInfo {
        RelativePath.require(relativeSessionPath, "relativeSessionPath");
        configLayers = configLayers == null ? List.of() : List.copyOf(configLayers);
    }

    /**
     * Whether this session promised to report liveness, and may therefore be finished for
     * failing to. An undeclared session ({@code null}) is not held to that promise.
     */
    public boolean expectsHeartbeat() {
        return Boolean.TRUE.equals(heartbeatExpected);
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
                relativeSessionPath, originCreatedAt, createdAt, finishedAt, false, null,
                null, null, List.of());
    }

    /**
     * Copy of this session info carrying what the session declared about reporting liveness.
     */
    public ProjectInstanceSessionInfo withHeartbeatExpected(Boolean heartbeatExpected) {
        return new ProjectInstanceSessionInfo(
                sessionId, repositoryId, instanceId, order,
                relativeSessionPath, originCreatedAt, createdAt, finishedAt, retained, heartbeatExpected,
                profilerCommandSource, profilerCommand, configLayers);
    }

    /** Copy of this session info carrying what the run was started with. */
    public ProjectInstanceSessionInfo withProfilerCommand(
            ConfigSource source, String command, List<AppliedConfigLayer> layers) {

        return new ProjectInstanceSessionInfo(
                sessionId, repositoryId, instanceId, order,
                relativeSessionPath, originCreatedAt, createdAt, finishedAt, retained, heartbeatExpected,
                source, command, layers);
    }
}
