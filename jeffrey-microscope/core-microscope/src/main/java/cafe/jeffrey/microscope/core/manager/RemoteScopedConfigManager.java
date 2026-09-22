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


package cafe.jeffrey.microscope.core.manager;

import cafe.jeffrey.hub.client.ScopedConfigClient;
import cafe.jeffrey.microscope.model.config.ScopedConfig;
import cafe.jeffrey.shared.common.config.ConfigScope;
import cafe.jeffrey.shared.common.config.ConfigType;

/** Talks to the hub that owns the project. */
public class RemoteScopedConfigManager implements ScopedConfigManager {

    private final ScopedConfigClient client;
    private final String workspaceId;
    private final String projectId;

    public RemoteScopedConfigManager(ScopedConfigClient client, String workspaceId, String projectId) {
        this.client = client;
        this.workspaceId = workspaceId;
        this.projectId = projectId;
    }

    @Override
    public ScopedConfig find() {
        return client.get(ConfigScope.PROJECT, workspaceId, projectId);
    }

    @Override
    public ScopedConfig upsert(ConfigType type, String value) {
        return client.upsert(ConfigScope.PROJECT, workspaceId, projectId, type, value);
    }

    @Override
    public ScopedConfig delete(ConfigType type) {
        return client.delete(ConfigScope.PROJECT, workspaceId, projectId, type);
    }
}
