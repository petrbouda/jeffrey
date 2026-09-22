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

package cafe.jeffrey.microscope.core.manager.workspace;

import cafe.jeffrey.microscope.model.config.ScopedConfig;
import cafe.jeffrey.shared.common.config.ConfigScope;
import cafe.jeffrey.shared.common.config.ConfigType;
import cafe.jeffrey.microscope.core.manager.project.ProjectsManager;
import cafe.jeffrey.microscope.model.workspace.WorkspaceInfo;

import java.util.List;
import java.util.Optional;

public interface WorkspaceManager {

    /**
     * Returns the cached workspace snapshot captured when this manager was resolved.
     * Never makes a network call.
     */
    WorkspaceInfo localInfo();

    /**
     * Returns a fresh workspace snapshot from the hub.
     * May involve a network call; falls back to OFFLINE status on failure.
     */
    WorkspaceInfo resolveInfo();

    /**
     * Returns the projects manager for managing multiple projects within the workspace.
     *
     * @return the projects manager
     */
    ProjectsManager projectsManager();

    /**
     * Everything the hub holds for this workspace: the global scope, the workspace's own, and each
     * of its projects, in merge order. One call because an editor always needs a scope together
     * with what it inherits.
     */
    List<ScopedConfig> listConfigs();

    /**
     * Stores one configuration value at the global or workspace scope. The hub validates it and
     * republishes the scope's file; the value reaches a JVM on its next start.
     */
    ScopedConfig upsertConfig(ConfigScope scope, ConfigType type, String value);

    /** Removes one configuration value from the global or workspace scope. */
    ScopedConfig deleteConfig(ConfigScope scope, ConfigType type);

    /**
     * Deletes the workspace from the repository.
     */
    void delete();

}
