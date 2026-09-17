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

import cafe.jeffrey.hub.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.model.workspace.WorkspaceInfo;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Base class for jobs that fan out across all workspaces: each tick iterates every workspace
 * whose directory exists and invokes {@link #executeOnWorkspace} once per workspace.
 */
public abstract class WorkspaceJob extends FanOutJob {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceJob.class);

    protected WorkspaceJob(WorkspacesManager workspacesManager) {
        super(workspacesManager);
    }

    @Override
    public void execute() {
        for (WorkspaceManager workspaceManager : workspacesManager.findAll()) {
            WorkspaceInfo workspaceInfo = workspaceManager.resolveInfo();
            Path workspacePath = workspaceInfo.location().toPath();
            if (!FileSystemUtils.isDirectory(workspacePath)) {
                LOG.debug("Workspace dir does not exist, or is invalid: job={} workspace_path={}",
                        getClass().getSimpleName(), workspacePath);
                continue;
            }
            visit("workspace_id=" + workspaceInfo.id() + " workspace_dir=" + workspacePath,
                    () -> executeOnWorkspace(workspaceManager));
        }
    }

    protected abstract void executeOnWorkspace(WorkspaceManager workspaceManager);
}
