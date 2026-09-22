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

import cafe.jeffrey.hub.core.config.ScopedConfigManager;
import cafe.jeffrey.hub.core.configuration.properties.SchedulerJobsProperties.JobConfig;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.model.config.ScopedConfigKey;
import cafe.jeffrey.hub.model.job.JobType;

import java.time.Duration;

/**
 * Keeps every scope's published file in step with what the hub holds.
 *
 * <p>Changes publish as they are made, so on a quiet volume this does nothing: each scope renders
 * to the same bytes the file already has and the publisher leaves it alone. It exists for what a
 * synchronous publish cannot cover — a hub that was down when someone edited, a volume that was not
 * mounted, a file someone deleted by hand.</p>
 *
 * <p>The database is the source of truth without exception: each scope is rendered from what it
 * holds and written out, and a scope holding nothing has its file removed. Nothing here reads a
 * file back, so a file edited by hand on the volume is replaced rather than believed.</p>
 */
public class ScopedConfigSynchronizerJob extends WorkspaceJob {

    private final Duration period;
    private final ScopedConfigManager configManager;

    public ScopedConfigSynchronizerJob(
            WorkspacesManager workspacesManager,
            JobConfig config,
            ScopedConfigManager configManager) {

        super(workspacesManager);
        this.period = config.period();
        this.configManager = configManager;
    }

    /**
     * The global scope belongs to no workspace, so it is handled once before the fan-out rather
     * than repeatedly inside it.
     */
    @Override
    public void execute() {
        visit("scope=GLOBAL", () -> configManager.publish(ScopedConfigKey.global()));
        super.execute();
    }

    @Override
    protected void executeOnWorkspace(WorkspaceManager workspaceManager) {
        String workspaceId = workspaceManager.resolveInfo().id();
        configManager.publish(ScopedConfigKey.workspace(workspaceId));

        for (ProjectManager project : workspaceManager.projectsManager().findAll()) {
            configManager.publish(ScopedConfigKey.project(workspaceId, project.info().id()));
        }
    }

    @Override
    public Duration period() {
        return period;
    }

    @Override
    public JobType jobType() {
        return JobType.SCOPED_CONFIG_SYNCHRONIZER;
    }
}
