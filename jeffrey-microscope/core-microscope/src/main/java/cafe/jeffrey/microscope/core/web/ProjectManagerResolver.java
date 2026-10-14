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
import cafe.jeffrey.microscope.core.manager.hub.HubManager;
import cafe.jeffrey.microscope.core.manager.hub.HubsManager;
import cafe.jeffrey.microscope.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.shared.common.exception.Exceptions;

/**
 * Resolves a (hubId, workspaceId, projectId) tuple to the corresponding
 * {@link ProjectManager}. Used by every workspace/project-scoped controller.
 */
public class ProjectManagerResolver {

    private final HubsManager hubsManager;

    public ProjectManagerResolver(HubsManager hubsManager) {
        this.hubsManager = hubsManager;
    }

    public HubManager resolveHub(String hubId) {
        return hubsManager.findById(hubId)
                .orElseThrow(() -> Exceptions.invalidRequest("Hub not found: " + hubId));
    }

    /**
     * Resolves a workspace of a hub. A hub that cannot be reached surfaces as its gRPC failure —
     * reported as the hub being unavailable — never as a workspace that is not there.
     */
    public WorkspaceManager resolveWorkspace(String hubId, String workspaceId) {
        return resolveHub(hubId).workspaceOrThrow(workspaceId)
                .orElseThrow(() -> Exceptions.workspaceNotFound(workspaceId));
    }

    /**
     * Resolves a project of a hub's workspace in one GetProject call: the hub checks that the
     * workspace holds the project and answers with all the manager needs. Not found means the
     * hub said so, whether of the workspace or the project; an unreachable hub is reported as
     * unavailable.
     */
    public ProjectManager resolve(String hubId, String workspaceId, String projectId) {
        return resolveHub(hubId).projectOrThrow(workspaceId, projectId)
                .orElseThrow(() -> Exceptions.projectNotFound(projectId));
    }
}
