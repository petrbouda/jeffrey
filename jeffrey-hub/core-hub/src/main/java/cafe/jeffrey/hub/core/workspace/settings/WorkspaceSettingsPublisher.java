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
package cafe.jeffrey.hub.core.workspace.settings;

import cafe.jeffrey.shared.common.model.repository.RemoteWorkspaceSettings;

/**
 * Publishes a workspace's effective profiler settings where the provisioner reads them: a
 * timestamped file under the workspace's settings directory on the shared volume. The hub
 * writes there and never reads back anything but its own files; it once also listed the
 * project, instance and session markers of the workspace from here, which the reconciler now
 * does through the pending index.
 */
public interface WorkspaceSettingsPublisher {

    /**
     * Writes a new settings version, unless the newest one already says the same.
     */
    void uploadSettings(RemoteWorkspaceSettings settings);

    /**
     * Deletes every settings version but the newest {@code keepMaxVersions}.
     */
    void removeLegacySettings(int keepMaxVersions);
}
