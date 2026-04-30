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

package cafe.jeffrey.local.persistence.api;

import java.util.List;
import java.util.Optional;

/**
 * Local registry of connected jeffrey-server instances.
 * Workspaces hosted on those servers are NOT stored locally — they are listed
 * live via gRPC from the connected server.
 */
public interface RemoteServersRepository {

    List<RemoteServerInfo> findAll();

    Optional<RemoteServerInfo> find(String serverId);

    RemoteServerInfo create(RemoteServerInfo serverInfo);

    void delete(String serverId);
}
