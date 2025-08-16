/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.appinitializer;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import pbouda.jeffrey.manager.SchedulerManager;
import pbouda.jeffrey.scheduler.job.descriptor.ProjectsSynchronizerJobDescriptor;
import pbouda.jeffrey.scheduler.job.descriptor.WorkspaceEventsReplicatorJobDescriptor;

public class GlobalJobsInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private final SchedulerManager schedulerManager;

    public GlobalJobsInitializer(SchedulerManager schedulerManager) {
        this.schedulerManager = schedulerManager;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        ConfigurableEnvironment environment = event.getApplicationContext().getEnvironment();

        boolean projectSynchronizerCreate = environment.getProperty(
                "jeffrey.job.projects-synchronizer.create-if-not-exists", Boolean.class, false);
        if (projectSynchronizerCreate) {
            schedulerManager.create(ProjectsSynchronizerJobDescriptor.of(environment));
        }

        boolean workspaceEventsReplicatorCreate = environment.getProperty(
                "jeffrey.job.workspace-events-replicator.create-if-not-exists", Boolean.class, false);
        if (workspaceEventsReplicatorCreate) {
            schedulerManager.create(new WorkspaceEventsReplicatorJobDescriptor());
        }
    }
}
