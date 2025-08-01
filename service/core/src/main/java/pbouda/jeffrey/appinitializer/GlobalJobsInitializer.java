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

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import pbouda.jeffrey.common.filesystem.HomeDirs;
import pbouda.jeffrey.configuration.AppConfiguration;
import pbouda.jeffrey.manager.SchedulerManager;
import pbouda.jeffrey.scheduler.job.descriptor.ProjectsSynchronizerJobDescriptor;

public class GlobalJobsInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private final SchedulerManager schedulerManager;
    private final HomeDirs homeDirs;

    public GlobalJobsInitializer(
            @Qualifier(AppConfiguration.GLOBAL_SCHEDULER_MANAGER_BEAN) SchedulerManager schedulerManager,
            HomeDirs homeDirs) {

        this.schedulerManager = schedulerManager;
        this.homeDirs = homeDirs;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        ConfigurableEnvironment environment = event.getApplicationContext().getEnvironment();

        boolean projectSynchronizerCreate = environment.getProperty(
                "jeffrey.job.projects-synchronizer.create-if-not-exists", Boolean.class, false);

        if (projectSynchronizerCreate) {
            ProjectsSynchronizerJobDescriptor jobDescriptor =
                    ProjectsSynchronizerJobDescriptor.of(homeDirs, environment);
            schedulerManager.create(jobDescriptor);
        }
    }
}
