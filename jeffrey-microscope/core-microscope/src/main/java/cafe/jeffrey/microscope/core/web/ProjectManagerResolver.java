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

package cafe.jeffrey.microscope.core.web;

import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.microscope.core.manager.remote.RemoteConnection;
import cafe.jeffrey.microscope.core.manager.remote.RemoteManager;
import cafe.jeffrey.microscope.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.shared.common.exception.Exceptions;

import java.util.Optional;

/**
 * Resolves a (workspaceId, projectId) tuple to the corresponding
 * {@link ProjectManager}. Used by every workspace/project-scoped controller.
 */
public class ProjectManagerResolver {

    private final RemoteManager remoteManager;

    public ProjectManagerResolver(RemoteManager remoteManager) {
        this.remoteManager = remoteManager;
    }

    /** The configured remote, or empty when none is configured. */
    public Optional<RemoteConnection> currentRemote() {
        return remoteManager.current();
    }

    /** The configured remote; not found when none is. */
    public RemoteConnection resolveRemote() {
        return remoteManager.require();
    }

    /**
     * Resolves a workspace of the remote. A remote that cannot be reached surfaces as its failure —
     * reported as the remote being unavailable — never as a workspace that is not there.
     */
    public WorkspaceManager resolveWorkspace(String workspaceId) {
        return resolveRemote().workspaceOrThrow(workspaceId)
                .orElseThrow(() -> Exceptions.workspaceNotFound(workspaceId));
    }

    /**
     * Resolves a project of one of the remote's workspaces in one call: the remote checks that the
     * workspace holds the project and answers with all the manager needs. Not found means the
     * remote said so, whether of the workspace or the project; an unreachable remote is reported as
     * unavailable.
     */
    public ProjectManager resolve(String workspaceId, String projectId) {
        return resolveRemote().projectOrThrow(workspaceId, projectId)
                .orElseThrow(() -> Exceptions.projectNotFound(projectId));
    }
}
