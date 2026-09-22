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


package cafe.jeffrey.hub.persistence.api;

import cafe.jeffrey.hub.model.config.ScopedConfigEntry;
import cafe.jeffrey.hub.model.config.ScopedConfigKey;
import cafe.jeffrey.shared.common.config.ConfigType;

import java.util.List;

/**
 * The stored configuration values, which are the source of truth; the files on the shared volume
 * are a projection of what is here.
 */
public interface ScopedConfigRepository {

    /** Stores a value, replacing whatever that scope held for the same type. */
    void upsert(ScopedConfigEntry entry);

    /** Everything one scope holds. */
    List<ScopedConfigEntry> find(ScopedConfigKey key);

    /**
     * Everything that applies to a workspace: the global entries, the workspace's own, and those of
     * every project in it. One query, because the editor and the synchronizer both want the whole
     * picture rather than a scope at a time.
     */
    List<ScopedConfigEntry> findForWorkspace(String workspaceId);

    /** Removes one value; removing what is not there is not an error. */
    void delete(ScopedConfigKey key, ConfigType type);

    /** Removes every value of a scope, for when the workspace or project itself goes. */
    void deleteAll(ScopedConfigKey key);
}
