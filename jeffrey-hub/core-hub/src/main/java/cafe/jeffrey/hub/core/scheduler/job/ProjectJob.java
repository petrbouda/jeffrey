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
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.core.scheduler.Job;
import cafe.jeffrey.hub.core.scheduler.JobContext;
import cafe.jeffrey.hub.core.scheduler.job.descriptor.JobDescriptor;
import cafe.jeffrey.shared.common.measure.Measuring;

import java.time.Duration;

/**
 * Base class for jobs that fan out across all projects in all workspaces. The
 * descriptor is constant for the lifetime of the job (resolved at startup from
 * {@code application.properties}); each tick iterates the live project list and
 * invokes {@link #execute} once per project.
 */
public abstract class ProjectJob<T extends JobDescriptor<T>> implements Job {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectJob.class);

    private final WorkspacesManager workspacesManager;
    protected final T jobDescriptor;

    protected ProjectJob(WorkspacesManager workspacesManager, T jobDescriptor) {
        this.workspacesManager = workspacesManager;
        this.jobDescriptor = jobDescriptor;
    }

    @Override
    public void execute(JobContext context) {
        String simpleName = this.getClass().getSimpleName();

        for (WorkspaceManager workspaceManager : workspacesManager.findAll()) {
            for (ProjectManager projectManager : workspaceManager.projectsManager().findAll()) {
                // Isolate per-project failures: one broken project (vanished session directory,
                // storage hiccup) must not abort the tick for every remaining project.
                try {
                    Duration elapsed = Measuring.r(() -> execute(projectManager, jobDescriptor, context));
                    LOG.debug("Job completed: job={} elapsed_ms={} workspace_id={} project_id={}",
                            simpleName, elapsed.toMillis(),
                            workspaceManager.resolveInfo().id(), projectManager.info().id());
                } catch (Exception e) {
                    LOG.error("Job failed for project, continuing with remaining projects: " +
                                    "job={} workspace_id={} project_id={}",
                            simpleName, workspaceManager.resolveInfo().id(), projectManager.info().id(), e);
                }
            }
        }
    }

    protected abstract void execute(ProjectManager projectManager, T jobDescriptor, JobContext context);
}
