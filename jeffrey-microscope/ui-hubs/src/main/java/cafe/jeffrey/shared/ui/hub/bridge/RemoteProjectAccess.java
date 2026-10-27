/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.shared.ui.hub.bridge;

import cafe.jeffrey.recordings.core.RecordingsDownloadManager;
import cafe.jeffrey.hub.client.manager.RemoteInstancesManager;
import cafe.jeffrey.hub.client.manager.RepositoryManager;

/**
 * Deployment-agnostic resolver from a (hubId, workspaceId, projectId) tuple to the per-project
 * remote managers used by the shared workspace controllers. Each deployment provides a bean that
 * delegates to its own project resolution machinery (microscope's {@code ProjectManagerResolver}
 * via {@code ProjectManager}).
 */
public interface RemoteProjectAccess {

    RemoteInstancesManager instancesManager(String hubId, String workspaceId, String projectId);

    RepositoryManager repositoryManager(String hubId, String workspaceId, String projectId);

    RecordingsDownloadManager recordingsDownloadManager(String hubId, String workspaceId, String projectId);
}
