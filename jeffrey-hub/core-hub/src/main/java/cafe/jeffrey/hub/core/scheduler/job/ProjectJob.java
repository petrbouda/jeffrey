/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package cafe.jeffrey.hub.core.scheduler.job;

import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;

/**
 * Base class for jobs that fan out across all projects in all workspaces: each tick
 * iterates the live project list and invokes {@link #execute(ProjectManager)} once per project.
 */
public abstract class ProjectJob extends FanOutJob {

    protected ProjectJob(WorkspacesManager workspacesManager) {
        super(workspacesManager);
    }

    @Override
    public void execute() {
        for (WorkspaceManager workspaceManager : workspacesManager.findAll()) {
            String workspaceId = workspaceManager.resolveInfo().id();
            for (ProjectManager projectManager : workspaceManager.projectsManager().findAll()) {
                visit("workspace_id=" + workspaceId + " project_id=" + projectManager.info().id(),
                        () -> execute(projectManager));
            }
        }
    }

    protected abstract void execute(ProjectManager projectManager);
}
