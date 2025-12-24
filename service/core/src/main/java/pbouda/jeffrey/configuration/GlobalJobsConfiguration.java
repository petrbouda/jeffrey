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

package pbouda.jeffrey.configuration;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pbouda.jeffrey.appinitializer.GlobalJobsInitializer;
import pbouda.jeffrey.manager.SchedulerManager;
import pbouda.jeffrey.manager.workspace.LiveWorkspacesManager;
import pbouda.jeffrey.project.repository.RemoteRepositoryStorage;
import pbouda.jeffrey.provider.api.repository.Repositories;
import pbouda.jeffrey.scheduler.PeriodicalScheduler;
import pbouda.jeffrey.scheduler.Scheduler;
import pbouda.jeffrey.scheduler.job.*;
import pbouda.jeffrey.scheduler.job.descriptor.JobDescriptorFactory;
import pbouda.jeffrey.storage.recording.api.RecordingStorage;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static pbouda.jeffrey.configuration.AppConfiguration.GLOBAL_SCHEDULER_MANAGER_BEAN;

/**
 * Configuration for GLOBAL/Workspace-level scheduler jobs.
 * These jobs operate across workspaces and use the global scheduler manager.
 */
@Configuration
public class GlobalJobsConfiguration {

    public static final String PROJECTS_SYNCHRONIZER_TRIGGER = "PROJECTS_SYNCHRONIZER_TRIGGER";

    private static final String GLOBAL_SCHEDULER = "GLOBAL_SCHEDULER";

    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);

    private final LiveWorkspacesManager liveWorkspacesManager;
    private final JobDescriptorFactory jobDescriptorFactory;
    private final SchedulerManager schedulerManager;
    private final Duration defaultPeriod;

    public GlobalJobsConfiguration(
            LiveWorkspacesManager liveWorkspacesManager,
            JobDescriptorFactory jobDescriptorFactory,
            @Qualifier(GLOBAL_SCHEDULER_MANAGER_BEAN) SchedulerManager schedulerManager,
            @Value("${jeffrey.job.default.period:}") Duration defaultPeriod) {

        this.liveWorkspacesManager = liveWorkspacesManager;
        this.jobDescriptorFactory = jobDescriptorFactory;
        this.schedulerManager = schedulerManager;
        this.defaultPeriod = defaultPeriod == null ? ONE_MINUTE : defaultPeriod;
    }

    @Bean(name = GLOBAL_SCHEDULER, destroyMethod = "close")
    public Scheduler globalScheduler(List<WorkspaceJob<?>> jobs) {
        return new PeriodicalScheduler(jobs, Duration.ofSeconds(5));
    }

    @Bean
    public GlobalJobsInitializer globalJobsInitializer() {
        return new GlobalJobsInitializer(schedulerManager);
    }

    @Bean
    public OrphanedProjectRecordingStorageCleanerJob orphanedProjectRecordingStorageCleanerJob(
            RecordingStorage recordingStorage,
            @Value("${jeffrey.job.orphaned-project-recording-storage-cleaner.period:}") Duration jobPeriod) {

        return new OrphanedProjectRecordingStorageCleanerJob(
                liveWorkspacesManager,
                schedulerManager,
                jobDescriptorFactory,
                recordingStorage,
                jobPeriod == null ? defaultPeriod : jobPeriod);
    }

    @Bean
    public ProjectsSynchronizerJob projectsSynchronizerJob(
            @Value("${jeffrey.job.projects-synchronizer.period:}") Duration jobPeriod,
            Repositories repositories,
            RemoteRepositoryStorage.Factory remoteRepositoryStorageFactory) {

        return new ProjectsSynchronizerJob(
                repositories,
                remoteRepositoryStorageFactory,
                liveWorkspacesManager,
                schedulerManager,
                jobDescriptorFactory,
                jobPeriod == null ? defaultPeriod : jobPeriod);
    }

    @Bean
    public WorkspaceEventsReplicatorJob workspaceEventsReplicatorJob(
            Clock clock,
            @Qualifier(PROJECTS_SYNCHRONIZER_TRIGGER) Runnable projectsSynchronizerTrigger,
            @Value("${jeffrey.job.workspace-events-replicator.period:}") Duration jobPeriod) {

        return new WorkspaceEventsReplicatorJob(
                liveWorkspacesManager,
                schedulerManager,
                jobDescriptorFactory,
                jobPeriod == null ? defaultPeriod : jobPeriod,
                clock,
                projectsSynchronizerTrigger);
    }

    @Bean
    public WorkspaceProfilerSettingsSynchronizerJob profilerSettingsSynchronizerJob(
            Repositories repositories,
            @Value("${jeffrey.job.profiler-settings-synchronizer.period:}") Duration jobPeriod) {

        return new WorkspaceProfilerSettingsSynchronizerJob(
                jobPeriod == null ? defaultPeriod : jobPeriod,
                repositories.newProfilerRepository(),
                liveWorkspacesManager,
                schedulerManager,
                repositories,
                jobDescriptorFactory);
    }

    @Bean(PROJECTS_SYNCHRONIZER_TRIGGER)
    public Runnable projectsSynchronizerTrigger(
            @Qualifier(GLOBAL_SCHEDULER) ObjectFactory<Scheduler> scheduler,
            ProjectsSynchronizerJob projectsSynchronizerJob) {
        return () -> scheduler.getObject().submitAndWait(projectsSynchronizerJob);
    }
}
