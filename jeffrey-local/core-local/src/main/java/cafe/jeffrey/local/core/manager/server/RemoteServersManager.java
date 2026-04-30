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

package cafe.jeffrey.local.core.manager.server;

import cafe.jeffrey.local.persistence.api.ServerAddress;

import java.util.List;
import java.util.Optional;

/**
 * Top-level registry of connected jeffrey-server instances on the local side.
 * Each connected server is exposed as a {@link RemoteServerManager} with its
 * own gRPC clients.
 */
public interface RemoteServersManager {

    record CreateServerRequest(String name, ServerAddress address) {
    }

    /**
     * Adds a new connected jeffrey-server.
     */
    RemoteServerManager create(CreateServerRequest request);

    /**
     * Returns all connected servers.
     */
    List<RemoteServerManager> findAll();

    /**
     * Looks up a server by its locally-generated id.
     */
    Optional<RemoteServerManager> findById(String serverId);
}
