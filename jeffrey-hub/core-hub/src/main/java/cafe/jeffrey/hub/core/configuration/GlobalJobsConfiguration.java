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

package cafe.jeffrey.hub.core.configuration;

import cafe.jeffrey.hub.core.HubJeffreyDirs;
import cafe.jeffrey.hub.core.appinitializer.ApplicationInitializer;
import cafe.jeffrey.hub.core.appinitializer.DefaultWorkspaceInitializer;
import cafe.jeffrey.hub.core.configuration.properties.DefaultWorkspaceProperties;
import cafe.jeffrey.hub.core.configuration.properties.SchedulerJobsProperties;
import cafe.jeffrey.hub.core.manager.storage.StorageOverviewCache;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.core.scheduler.*;
import cafe.jeffrey.hub.core.scheduler.job.*;
import cafe.jeffrey.hub.core.scheduler.job.descriptor.ProfilerSettingsSynchronizerJobDescriptor;
import cafe.jeffrey.hub.core.streaming.SessionFinisher;
import cafe.jeffrey.hub.core.workspace.reconcile.WorkspaceReconciler;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
import cafe.jeffrey.shared.common.model.job.JobType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.util.List;

import static cafe.jeffrey.hub.core.configuration.HubAppConfiguration.GLOBAL_SCHEDULER;

/**
 * Configuration for all background scheduler jobs. Job configuration (enabled
 * flag, period, params) comes from {@link SchedulerJobsProperties} which merges
 * built-in defaults with overrides in {@code application.properties}.
 * <p>
 * The single {@link Scheduler} bean filters all submitted jobs by their
 * enabled flag at construction; disabled jobs are still constructed but never
 * registered for periodic execution.
 */
@Configuration
public class GlobalJobsConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalJobsConfiguration.class);

    private final WorkspacesManager workspacesManager;
    private final SchedulerJobsProperties schedulerJobsProperties;

    public GlobalJobsConfiguration(
            WorkspacesManager workspacesManager,
            SchedulerJobsProperties schedulerJobsProperties) {

        this.workspacesManager = workspacesManager;
        this.schedulerJobsProperties = schedulerJobsProperties;
    }

    @Bean(name = GLOBAL_SCHEDULER, destroyMethod = "close")
    public Scheduler scheduler(List<Job> jobs) {
        List<Job> enabled = jobs.stream()
                .filter(j -> {
                    boolean on = schedulerJobsProperties.forType(j.jobType()).enabled();
                    if (!on) {
                        LOG.info("Scheduler job disabled, skipping registration: job_type={}", j.jobType());
                    }
                    return on;
                })
                .toList();
        LOG.info("Registered scheduler jobs: enabled={} total={} fan_out_pool_size={}",
                enabled.size(), jobs.size(), schedulerJobsProperties.getFanOutPoolSize());
        return new PeriodicalScheduler(enabled, schedulerJobsProperties.getFanOutPoolSize());
    }

    @Bean
    public ApplicationInitializer applicationInitializer(HubPlatformRepositories platformRepositories) {
        return new ApplicationInitializer(platformRepositories.newProfilerRepository());
    }

    @Bean
    public DefaultWorkspaceInitializer defaultWorkspaceInitializer(
            DefaultWorkspaceProperties defaultWorkspaceProperties) {
        return new DefaultWorkspaceInitializer(workspacesManager, defaultWorkspaceProperties);
    }

    @Bean
    public WorkspaceReconciler workspaceReconciler(
            Clock clock,
            HubJeffreyDirs jeffreyDirs,
            HubPlatformRepositories platformRepositories,
            SessionFinisher sessionFinisher,
            TransactionOperations hubTransactionOperations) {

        return new WorkspaceReconciler(
                clock,
                jeffreyDirs,
                platformRepositories,
                sessionFinisher,
                hubTransactionOperations);
    }

    @Bean
    public WorkspaceReconcilerJob workspaceReconcilerJob(
            WorkspaceReconciler workspaceReconciler,
            HubJeffreyDirs jeffreyDirs,
            DefaultWorkspaceProperties defaultWorkspaceProperties,
            @Value("${jeffrey.hub.workspaces.auto-create:false}") boolean autoCreateWorkspaces) {

        return new WorkspaceReconcilerJob(
                workspacesManager,
                workspaceReconciler,
                jeffreyDirs,
                defaultWorkspaceProperties,
                autoCreateWorkspaces,
                schedulerJobsProperties.forType(JobType.WORKSPACE_RECONCILER).period());
    }


    @Bean
    public ProfilerSettingsSynchronizerJob profilerSettingsSynchronizerJob(
            HubPlatformRepositories platformRepositories) {

        SchedulerJobsProperties.JobConfig config =
                schedulerJobsProperties.forType(JobType.PROFILER_SETTINGS_SYNCHRONIZER);

        return new ProfilerSettingsSynchronizerJob(
                config.period(),
                platformRepositories.newProfilerRepository(),
                workspacesManager,
                ProfilerSettingsSynchronizerJobDescriptor.of(config.params()),
                platformRepositories);
    }

    @Bean
    public TempDirectoryCleanerJob tempDirectoryCleanerJob(HubJeffreyDirs jeffreyDirs, Clock clock) {
        return new TempDirectoryCleanerJob(
                jeffreyDirs.temp(),
                clock,
                schedulerJobsProperties.forType(JobType.TEMP_DIRECTORY_CLEANER));
    }

    @Bean
    public DeletedProjectsCleanerJob deletedProjectsCleanerJob(
            HubPlatformRepositories platformRepositories, Clock clock) {

        return new DeletedProjectsCleanerJob(
                platformRepositories.newProjectsRepository(),
                clock,
                schedulerJobsProperties.forType(JobType.DELETED_PROJECTS_CLEANER));
    }

    @Bean
    public StorageOverviewRefresherJob storageOverviewRefresherJob(StorageOverviewCache storageOverviewCache) {
        return new StorageOverviewRefresherJob(
                storageOverviewCache,
                schedulerJobsProperties.forType(JobType.STORAGE_OVERVIEW_REFRESHER));
    }

    @Bean
    public JobRegistry jobRegistry() {
        return new JobRegistry(schedulerJobsProperties);
    }

}
