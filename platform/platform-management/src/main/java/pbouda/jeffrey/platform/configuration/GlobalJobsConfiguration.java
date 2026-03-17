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

package pbouda.jeffrey.platform.configuration;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pbouda.jeffrey.platform.appinitializer.ApplicationInitializer;
import pbouda.jeffrey.platform.configuration.properties.JobProperties;
import pbouda.jeffrey.platform.manager.SchedulerManager;
import pbouda.jeffrey.platform.manager.workspace.LiveWorkspacesManager;
import pbouda.jeffrey.platform.queue.PersistentQueue;
import pbouda.jeffrey.platform.scheduler.*;
import pbouda.jeffrey.platform.scheduler.job.*;
import pbouda.jeffrey.platform.scheduler.job.descriptor.JobDescriptorFactory;
import pbouda.jeffrey.platform.workspace.consumer.WorkspaceEventConsumer;
import pbouda.jeffrey.provider.platform.repository.PlatformRepositories;
import pbouda.jeffrey.shared.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.shared.folderqueue.FolderQueue;
import pbouda.jeffrey.storage.recording.api.RecordingStorage;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static pbouda.jeffrey.platform.configuration.AppConfiguration.GLOBAL_SCHEDULER_MANAGER_BEAN;

/**
 * Configuration for GLOBAL/Workspace-level scheduler jobs.
 * These jobs operate across workspaces and use the global scheduler manager.
 */
@Configuration
public class GlobalJobsConfiguration {

    public static final String PROJECTS_SYNCHRONIZER_TRIGGER = "PROJECTS_SYNCHRONIZER_TRIGGER";

    private static final String GLOBAL_SCHEDULER = "GLOBAL_SCHEDULER";

    private final LiveWorkspacesManager liveWorkspacesManager;
    private final JobDescriptorFactory jobDescriptorFactory;
    private final SchedulerManager schedulerManager;
    private final JobProperties jobProperties;

    public GlobalJobsConfiguration(
            LiveWorkspacesManager liveWorkspacesManager,
            JobDescriptorFactory jobDescriptorFactory,
            @Qualifier(GLOBAL_SCHEDULER_MANAGER_BEAN) SchedulerManager schedulerManager,
            JobProperties jobProperties) {

        this.liveWorkspacesManager = liveWorkspacesManager;
        this.jobDescriptorFactory = jobDescriptorFactory;
        this.schedulerManager = schedulerManager;
        this.jobProperties = jobProperties;
    }

    @Bean(name = GLOBAL_SCHEDULER, destroyMethod = "close")
    public Scheduler globalScheduler(List<Job> jobs) {
        return new PeriodicalScheduler(jobs);
    }

    @Bean
    public ApplicationInitializer applicationInitializer(PlatformRepositories platformRepositories) {
        return new ApplicationInitializer(schedulerManager, platformRepositories.newProfilerRepository());
    }

    @Bean
    public OrphanedProjectRecordingStorageCleanerJob orphanedProjectRecordingStorageCleanerJob(
            RecordingStorage recordingStorage) {

        return new OrphanedProjectRecordingStorageCleanerJob(
                liveWorkspacesManager,
                schedulerManager,
                jobDescriptorFactory,
                recordingStorage,
                jobProperties.resolvePeriod("orphaned-project-recording-storage-cleaner"));
    }

    @Bean
    public ProjectsSynchronizerJob projectsSynchronizerJob(
            List<WorkspaceEventConsumer> consumers,
            PersistentQueue<WorkspaceEvent> workspaceEventQueue) {

        return new ProjectsSynchronizerJob(
                consumers,
                workspaceEventQueue,
                liveWorkspacesManager,
                schedulerManager,
                jobDescriptorFactory,
                jobProperties.resolvePeriod("projects-synchronizer"));
    }

    @Bean
    public WorkspaceEventsReplicatorJob workspaceEventsReplicatorJob(
            Clock clock,
            JeffreyDirs jeffreyDirs,
            PersistentQueue<WorkspaceEvent> workspaceEventQueue,
            @Qualifier(PROJECTS_SYNCHRONIZER_TRIGGER) SchedulerTrigger projectsSynchronizerTrigger,
            @Value("${jeffrey.platform.workspace.auto-create-from-events:true}") boolean autoCreateWorkspaces) {

        return new WorkspaceEventsReplicatorJob(
                liveWorkspacesManager,
                jobProperties.resolvePeriod("workspace-events-replicator", Duration.ofSeconds(5)),
                clock,
                autoCreateWorkspaces,
                new FolderQueue(jeffreyDirs.workspaceEvents(), clock),
                workspaceEventQueue,
                projectsSynchronizerTrigger);
    }

    @Bean
    public WorkspaceProfilerSettingsSynchronizerJob profilerSettingsSynchronizerJob(
            PlatformRepositories platformRepositories) {

        return new WorkspaceProfilerSettingsSynchronizerJob(
                jobProperties.resolvePeriod("profiler-settings-synchronizer"),
                platformRepositories.newProfilerRepository(),
                liveWorkspacesManager,
                schedulerManager,
                platformRepositories,
                jobDescriptorFactory);
    }

    @Bean
    public DataRetentionJob dataRetentionJob(
            PlatformRepositories platformRepositories,
            PersistentQueue<WorkspaceEvent> workspaceEventQueue,
            Clock clock,
            @Value("${jeffrey.platform.retention.queue-events:31d}") Duration queueEventsRetention,
            @Value("${jeffrey.platform.retention.messages:31d}") Duration messagesRetention,
            @Value("${jeffrey.platform.retention.alerts:31d}") Duration alertsRetention) {

        return new DataRetentionJob(
                platformRepositories.newMessageRetentionCleanup(),
                platformRepositories.newAlertRetentionCleanup(),
                workspaceEventQueue,
                clock,
                jobProperties.resolvePeriod("data-retention", Duration.ofDays(1)),
                queueEventsRetention,
                messagesRetention,
                alertsRetention);
    }

    @Bean(PROJECTS_SYNCHRONIZER_TRIGGER)
    public SchedulerTrigger projectsSynchronizerTrigger(
            @Qualifier(GLOBAL_SCHEDULER) ObjectFactory<Scheduler> scheduler,
            ProjectsSynchronizerJob projectsSynchronizerJob) {
        return new SchedulerTriggerImpl(scheduler, projectsSynchronizerJob);
    }
}
