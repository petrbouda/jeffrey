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

package pbouda.jeffrey.server.core.configuration;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pbouda.jeffrey.server.core.ServerJeffreyDirs;
import pbouda.jeffrey.server.core.appinitializer.ApplicationInitializer;
import pbouda.jeffrey.server.core.configuration.properties.JobProperties;
import pbouda.jeffrey.server.core.manager.SchedulerManager;
import pbouda.jeffrey.server.core.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.server.core.scheduler.*;
import pbouda.jeffrey.server.core.scheduler.job.DataRetentionJob;
import pbouda.jeffrey.server.core.scheduler.job.ProjectsSynchronizerJob;
import pbouda.jeffrey.server.core.scheduler.job.WorkspaceEventsReplicatorJob;
import pbouda.jeffrey.server.core.scheduler.job.WorkspaceProfilerSettingsSynchronizerJob;
import pbouda.jeffrey.server.core.scheduler.job.descriptor.JobDescriptorFactory;
import pbouda.jeffrey.server.core.workspace.consumer.WorkspaceEventConsumer;
import pbouda.jeffrey.server.persistence.repository.JdbcAlertRepository;
import pbouda.jeffrey.server.persistence.repository.JdbcMessageRepository;
import pbouda.jeffrey.server.persistence.repository.ServerPlatformRepositories;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.shared.folderqueue.FolderQueue;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;
import pbouda.jeffrey.shared.persistentqueue.PersistentQueue;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static pbouda.jeffrey.server.core.configuration.ServerAppConfiguration.GLOBAL_SCHEDULER_MANAGER_BEAN;
import static pbouda.jeffrey.server.core.configuration.ServerAppConfiguration.PROJECTS_SYNCHRONIZER_TRIGGER;

/**
 * Configuration for GLOBAL/Workspace-level scheduler jobs.
 * These jobs operate across workspaces and use the global scheduler manager.
 */
@Configuration
public class GlobalJobsConfiguration {

    private static final String GLOBAL_SCHEDULER = "GLOBAL_SCHEDULER";

    private final WorkspacesManager workspacesManager;
    private final JobDescriptorFactory jobDescriptorFactory;
    private final SchedulerManager schedulerManager;
    private final JobProperties jobProperties;

    public GlobalJobsConfiguration(
            WorkspacesManager workspacesManager,
            JobDescriptorFactory jobDescriptorFactory,
            @Qualifier(GLOBAL_SCHEDULER_MANAGER_BEAN) SchedulerManager schedulerManager,
            JobProperties jobProperties) {

        this.workspacesManager = workspacesManager;
        this.jobDescriptorFactory = jobDescriptorFactory;
        this.schedulerManager = schedulerManager;
        this.jobProperties = jobProperties;
    }

    @Bean(name = GLOBAL_SCHEDULER, destroyMethod = "close")
    public Scheduler globalScheduler(List<Job> jobs) {
        return new PeriodicalScheduler(jobs);
    }

    @Bean
    public ApplicationInitializer applicationInitializer(ServerPlatformRepositories platformRepositories) {
        return new ApplicationInitializer(schedulerManager, platformRepositories.newProfilerRepository());
    }

    @Bean
    public ProjectsSynchronizerJob projectsSynchronizerJob(
            List<WorkspaceEventConsumer> consumers,
            PersistentQueue<WorkspaceEvent> workspaceEventQueue) {

        return new ProjectsSynchronizerJob(
                consumers,
                workspaceEventQueue,
                workspacesManager,
                schedulerManager,
                jobDescriptorFactory,
                jobProperties.resolvePeriod("projects-synchronizer"));
    }

    @Bean
    public WorkspaceEventsReplicatorJob workspaceEventsReplicatorJob(
            Clock clock,
            ServerJeffreyDirs jeffreyDirs,
            PersistentQueue<WorkspaceEvent> workspaceEventQueue,
            @Qualifier(PROJECTS_SYNCHRONIZER_TRIGGER) SchedulerTrigger projectsSynchronizerTrigger,
            @Value("${jeffrey.server.platform.workspace.auto-create-from-events:true}") boolean autoCreateWorkspaces) {

        return new WorkspaceEventsReplicatorJob(
                workspacesManager,
                jobProperties.resolvePeriod("workspace-events-replicator", Duration.ofSeconds(5)),
                clock,
                autoCreateWorkspaces,
                new FolderQueue(jeffreyDirs.workspaceEvents(), clock),
                workspaceEventQueue,
                projectsSynchronizerTrigger);
    }

    @Bean
    public WorkspaceProfilerSettingsSynchronizerJob profilerSettingsSynchronizerJob(
            ServerPlatformRepositories platformRepositories) {

        return new WorkspaceProfilerSettingsSynchronizerJob(
                jobProperties.resolvePeriod("profiler-settings-synchronizer"),
                platformRepositories.newProfilerRepository(),
                workspacesManager,
                schedulerManager,
                platformRepositories,
                jobDescriptorFactory);
    }

    @Bean
    public DataRetentionJob dataRetentionJob(
            DatabaseClientProvider databaseClientProvider,
            PersistentQueue<WorkspaceEvent> workspaceEventQueue,
            Clock clock,
            @Value("${jeffrey.server.platform.retention.queue-events:31d}") Duration queueEventsRetention,
            @Value("${jeffrey.server.platform.retention.messages:31d}") Duration messagesRetention,
            @Value("${jeffrey.server.platform.retention.alerts:31d}") Duration alertsRetention) {

        return new DataRetentionJob(
                new JdbcMessageRepository(null, databaseClientProvider),
                new JdbcAlertRepository(null, databaseClientProvider),
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
