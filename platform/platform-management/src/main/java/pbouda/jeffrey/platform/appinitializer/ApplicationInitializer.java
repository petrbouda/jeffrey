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

package pbouda.jeffrey.platform.appinitializer;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import pbouda.jeffrey.shared.common.model.ProfilerInfo;
import pbouda.jeffrey.platform.manager.SchedulerManager;
import pbouda.jeffrey.platform.scheduler.job.descriptor.OrphanedProjectRecordingStorageCleanerJobDescriptor;
import pbouda.jeffrey.platform.scheduler.job.descriptor.ProjectsSynchronizerJobDescriptor;
import pbouda.jeffrey.platform.scheduler.job.descriptor.WorkspaceEventsReplicatorJobDescriptor;
import pbouda.jeffrey.platform.scheduler.job.descriptor.WorkspaceProfilerSettingsSynchronizerJobDescriptor;
import pbouda.jeffrey.provider.platform.repository.ProfilerRepository;

public class ApplicationInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private final SchedulerManager schedulerManager;
    private final ProfilerRepository profilerRepository;

    public ApplicationInitializer(SchedulerManager schedulerManager, ProfilerRepository profilerRepository) {
        this.schedulerManager = schedulerManager;
        this.profilerRepository = profilerRepository;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        ConfigurableEnvironment environment = event.getApplicationContext().getEnvironment();

        initializeGlobalJobs(environment);
        initializeProfilerSettings(environment);
    }

    private void initializeGlobalJobs(ConfigurableEnvironment environment) {
        boolean projectSynchronizerCreate = environment.getProperty(
                "jeffrey.job.projects-synchronizer.create-if-not-exists", Boolean.class, true);
        if (projectSynchronizerCreate) {
            schedulerManager.create(ProjectsSynchronizerJobDescriptor.of(environment));
        }

        boolean workspaceEventsReplicatorCreate = environment.getProperty(
                "jeffrey.job.workspace-events-replicator.create-if-not-exists", Boolean.class, true);
        if (workspaceEventsReplicatorCreate) {
            schedulerManager.create(new WorkspaceEventsReplicatorJobDescriptor());
        }

        boolean profileSynchronizerCreate = environment.getProperty(
                "jeffrey.job.profiler-settings-synchronizer.create-if-not-exists", Boolean.class, true);
        if (profileSynchronizerCreate) {
            schedulerManager.create(WorkspaceProfilerSettingsSynchronizerJobDescriptor.of(environment));
        }

        boolean orphanedProjectCleanerCreate = environment.getProperty(
                "jeffrey.job.orphaned-project-recording-storage-cleaner.create-if-not-exists", Boolean.class, true);
        if (orphanedProjectCleanerCreate) {
            schedulerManager.create(new OrphanedProjectRecordingStorageCleanerJobDescriptor());
        }
    }

    private void initializeProfilerSettings(ConfigurableEnvironment environment) {
        boolean createGlobalSettings = environment.getProperty(
                "jeffrey.profiler.global-settings.create-if-not-exists", Boolean.class, false);
        String globalCommand = environment.getProperty(
                "jeffrey.profiler.global-settings.command", String.class, "");

        if (createGlobalSettings && !globalCommand.isBlank()) {
            profilerRepository.upsertSettings(new ProfilerInfo(null, null, globalCommand));
        }
    }
}
