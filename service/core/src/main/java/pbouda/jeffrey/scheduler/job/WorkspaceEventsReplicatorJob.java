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

package pbouda.jeffrey.scheduler.job;

import pbouda.jeffrey.common.model.job.JobType;
import pbouda.jeffrey.manager.SchedulerManager;
import pbouda.jeffrey.manager.WorkspaceManager;
import pbouda.jeffrey.manager.WorkspacesManager;
import pbouda.jeffrey.scheduler.job.descriptor.JobDescriptorFactory;
import pbouda.jeffrey.scheduler.job.descriptor.WorkspaceEventsReplicatorJobDescriptor;

import java.time.Duration;

public class WorkspaceEventsReplicatorJob extends WorkspaceJob<WorkspaceEventsReplicatorJobDescriptor> {

    private final boolean removeReplicatedEvents;
    private final Duration period;
    private final Runnable migrationCallback;

    public WorkspaceEventsReplicatorJob(
            boolean removeReplicatedEvents,
            WorkspacesManager workspacesManager,
            SchedulerManager schedulerManager,
            JobDescriptorFactory jobDescriptorFactory,
            Duration period,
            Runnable migrationCallback) {

        super(workspacesManager, schedulerManager, jobDescriptorFactory);
        this.removeReplicatedEvents = removeReplicatedEvents;
        this.period = period;
        this.migrationCallback = migrationCallback;
    }

    @Override
    protected void executeOnWorkspace(
            WorkspaceManager workspaceManager, WorkspaceEventsReplicatorJobDescriptor jobDescriptor) {

        // Replicate events from remote workspace to the local workspace
        long migrated = workspaceManager.replicate(removeReplicatedEvents);

        if (migrated > 0) {
            // Execute after successful migration
            migrationCallback.run();
        }
    }

    @Override
    public Duration period() {
        return period;
    }

    @Override
    public JobType jobType() {
        return JobType.WORKSPACE_EVENTS_REPLICATOR;
    }
}
