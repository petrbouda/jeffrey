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

import cafe.jeffrey.hub.core.config.ScopedConfigAdopter;
import cafe.jeffrey.hub.core.config.ScopedConfigManager;
import cafe.jeffrey.hub.core.configuration.properties.SchedulerJobsProperties.JobConfig;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.model.config.ScopedConfigKey;
import cafe.jeffrey.hub.model.job.JobType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Keeps every scope's published file in step with what the hub holds.
 *
 * <p>Changes publish as they are made, so on a quiet volume this does nothing: each scope renders
 * to the same bytes the file already has and the publisher leaves it alone. It exists for what a
 * synchronous publish cannot cover — a hub that was down when someone edited, a volume that was not
 * mounted, a file someone deleted by hand.</p>
 *
 * <p><b>It never deletes a file.</b> A file whose values are missing from the database is adopted
 * back into it instead. The hub already treats this volume as the recoverable truth, rebuilding
 * projects, instances and sessions from the markers on it; configuration follows the same rule, so
 * a hub that comes up on an empty database recovers every workspace's configuration rather than
 * wiping it on the first tick. Removing a value is an explicit act, and only that removes a file.</p>
 */
public class ScopedConfigSynchronizerJob extends WorkspaceJob {

    private static final Logger LOG = LoggerFactory.getLogger(ScopedConfigSynchronizerJob.class);

    private final Duration period;
    private final ScopedConfigManager configManager;
    private final ScopedConfigAdopter adopter;

    public ScopedConfigSynchronizerJob(
            WorkspacesManager workspacesManager,
            JobConfig config,
            ScopedConfigManager configManager,
            ScopedConfigAdopter adopter) {

        super(workspacesManager);
        this.period = config.period();
        this.configManager = configManager;
        this.adopter = adopter;
    }

    /**
     * The global scope belongs to no workspace, so it is handled once before the fan-out rather
     * than repeatedly inside it.
     */
    @Override
    public void execute() {
        visit("scope=GLOBAL", () -> synchronize(ScopedConfigKey.global()));
        super.execute();
    }

    @Override
    protected void executeOnWorkspace(WorkspaceManager workspaceManager) {
        String workspaceId = workspaceManager.resolveInfo().id();
        synchronize(ScopedConfigKey.workspace(workspaceId));

        for (ProjectManager project : workspaceManager.projectsManager().findAll()) {
            synchronize(ScopedConfigKey.project(workspaceId, project.info().id()));
        }
    }

    private void synchronize(ScopedConfigKey key) {
        if (adopter.adopt(key)) {
            LOG.info("Adopted a published configuration file that the database did not know: "
                            + "scope={} workspace_id={} project_id={}",
                    key.scope(), key.workspaceId(), key.projectId());
            return;
        }
        configManager.publish(key);
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
