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

package cafe.jeffrey.performance.analyst.web;

import cafe.jeffrey.performance.analyst.manager.server.RemoteServerManager;
import cafe.jeffrey.performance.analyst.manager.server.RemoteServersManager;
import cafe.jeffrey.shared.common.exception.Exceptions;

/**
 * Resolves a {@code serverId} to its {@link RemoteServerManager}. Used by every
 * workspace/project-scoped controller.
 */
public class ServerResolver {

    private final RemoteServersManager remoteServersManager;

    public ServerResolver(RemoteServersManager remoteServersManager) {
        this.remoteServersManager = remoteServersManager;
    }

    public RemoteServerManager resolveServer(String serverId) {
        return remoteServersManager.findById(serverId)
                .orElseThrow(() -> Exceptions.invalidRequest("Remote server not found: " + serverId));
    }
}
