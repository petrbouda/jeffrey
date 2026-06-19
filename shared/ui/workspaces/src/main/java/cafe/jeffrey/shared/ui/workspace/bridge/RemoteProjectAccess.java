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

package cafe.jeffrey.shared.ui.workspace.bridge;

import cafe.jeffrey.recordings.core.RecordingsDownloadManager;
import cafe.jeffrey.hub.client.manager.RemoteInstancesManager;
import cafe.jeffrey.hub.client.manager.RepositoryManager;

/**
 * Deployment-agnostic resolver from a (hubId, workspaceId, projectId) tuple to the per-project
 * remote managers used by the shared workspace controllers. Each deployment provides a bean that
 * delegates to its own project resolution machinery (microscope's {@code ProjectManagerResolver}
 * via {@code ProjectManager}; the analyst's {@code RemoteProjectResolver}).
 */
public interface RemoteProjectAccess {

    RemoteInstancesManager instancesManager(String hubId, String workspaceId, String projectId);

    RepositoryManager repositoryManager(String hubId, String workspaceId, String projectId);

    RecordingsDownloadManager recordingsDownloadManager(String hubId, String workspaceId, String projectId);
}
