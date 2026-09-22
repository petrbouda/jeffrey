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

package cafe.jeffrey.shared.common.model.repository;

import java.util.List;

/**
 * Session metadata persisted as {@code .session-info.json} in the session
 * directory. The profiler fields document which configuration layer supplied the
 * session's command ({@code ConfigSource} name) and the resolved command itself,
 * and {@code configLayers} names the hub-published files that were merged, each
 * with the digest of the bytes as they were read. Together they let a reader say
 * not only what a session runs with but whether that is still current.
 *
 * <p>{@code heartbeatExpected} declares whether anything in this run will report
 * liveness — the {@code jeffrey-heartbeat} library, which is an ordinary
 * dependency of the application. The provisioner cannot detect that on its own:
 * whether the library is on the class path is a build-time fact, invisible to the
 * tool that writes the JVM arguments. So it is <em>declared</em>, from
 * {@code heartbeat.enabled} in the provisioner's configuration.
 *
 * <p>It is {@code null} in files written by older provisioners, and null means
 * <em>unknown</em> rather than false: the hub never applies the heartbeat deadline
 * to a session that did not promise to report, because failing to report liveness
 * it never promised is not evidence that it ended.</p>
 */
public record RemoteProjectInstanceSession(
        String sessionId,
        String projectId,
        String workspaceId,
        String instanceId,
        long createdAt,
        int order,
        String relativeSessionPath,
        String profilerCommandSource,
        String profilerCommand,
        Boolean heartbeatExpected,
        List<AppliedConfigLayer> configLayers) {

    /** Never null, so a caller can iterate without a guard; empty when no layer was merged. */
    public RemoteProjectInstanceSession {
        configLayers = configLayers == null ? List.of() : List.copyOf(configLayers);
    }
}
