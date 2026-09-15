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

/**
 * Session metadata persisted as {@code .session-info.json} in the session
 * directory. The two profiler fields document which source the session's
 * async-profiler command was resolved from ({@code ProfilerSettingsSource}
 * name) and the resolved command itself; both are null in files written by
 * older provisioners.
 *
 * <p>{@code agentAttached} declares whether this run was provisioned with the
 * Jeffrey agent, which is the only thing that writes the liveness files the hub
 * finishes a session from. It is {@code null} in files written by older
 * provisioners, and null means <em>unknown</em> rather than false: the hub
 * never applies the heartbeat deadline to a session that did not declare an
 * agent, because a session nobody promised would report liveness must not be
 * finished for failing to report it.</p>
 */
public record RemoteProjectInstanceSession(
        String sessionId,
        String projectId,
        String workspaceId,
        String instanceId,
        long createdAt,
        int order,
        String relativeSessionPath,
        String profilerSettingsSource,
        String profilerCommand,
        Boolean agentAttached) {
}
