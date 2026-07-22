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

package cafe.jeffrey.hub.core.scheduler.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import cafe.jeffrey.hub.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.core.scheduler.Job;
import cafe.jeffrey.hub.core.scheduler.JobContext;
import cafe.jeffrey.hub.core.scheduler.job.descriptor.JobDescriptor;
import cafe.jeffrey.shared.common.measure.Measuring;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Base class for jobs that fan out across all workspaces. The descriptor is
 * constant for the lifetime of the job (resolved at startup from
 * {@code application.properties}); each tick iterates all workspaces and
 * invokes {@link #executeOnWorkspace} once per workspace.
 */
public abstract class WorkspaceJob<T extends JobDescriptor<T>> implements Job {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceJob.class);

    private final WorkspacesManager workspacesManager;
    protected final T jobDescriptor;

    public WorkspaceJob(WorkspacesManager workspacesManager, T jobDescriptor) {
        this.workspacesManager = workspacesManager;
        this.jobDescriptor = jobDescriptor;
    }

    @Override
    public ExecutorGroup executorGroup() {
        return ExecutorGroup.PROJECT_FAN_OUT;
    }

    @Override
    public void execute(JobContext context) {
        String simpleName = this.getClass().getSimpleName();

        for (WorkspaceManager workspaceManager : workspacesManager.findAll()) {
            WorkspaceInfo workspaceInfo = workspaceManager.resolveInfo();
            Path workspacePath = workspaceInfo.location().toPath();

            if (!FileSystemUtils.isDirectory(workspacePath)) {
                LOG.debug("Workspace dir does not exist, or is invalid: job={} workspace_path={}",
                        simpleName, workspacePath);
                continue;
            }

            LOG.debug("Executing Job: job={} workspace={} workspace_dir={}",
                    simpleName, workspaceInfo.id(), workspacePath);

            // Isolate per-workspace failures: one broken workspace must not abort the tick
            // for every remaining workspace.
            try {
                Duration elapsed = Measuring.r(() -> executeOnWorkspace(workspaceManager, jobDescriptor, context));
                LOG.debug("Job completed: job={} elapsed_ms={} workspace_id={} workspace_dir={}",
                        simpleName, elapsed.toMillis(), workspaceInfo.id(), workspacePath);
            } catch (Exception e) {
                LOG.error("Job failed for workspace, continuing with remaining workspaces: " +
                        "job={} workspace_id={}", simpleName, workspaceInfo.id(), e);
            }
        }
    }

    protected abstract void executeOnWorkspace(WorkspaceManager workspaceManager, T jobDescriptor, JobContext context);
}
