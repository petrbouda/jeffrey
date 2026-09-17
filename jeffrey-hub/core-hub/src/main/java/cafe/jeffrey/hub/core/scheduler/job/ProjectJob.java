/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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
