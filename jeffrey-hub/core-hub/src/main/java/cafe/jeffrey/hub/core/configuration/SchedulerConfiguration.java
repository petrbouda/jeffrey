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
import cafe.jeffrey.hub.core.appinitializer.SchedulerInitializer;
import cafe.jeffrey.hub.core.configuration.properties.DefaultWorkspaceProperties;
import cafe.jeffrey.hub.core.configuration.properties.SchedulerJobsProperties;
import cafe.jeffrey.hub.core.configuration.properties.SchedulerJobsProperties.JobConfig;
import cafe.jeffrey.hub.core.configuration.properties.WorkspacesProperties;
import cafe.jeffrey.hub.core.manager.storage.StorageOverviewCache;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.core.project.session.SessionFinisher;
import cafe.jeffrey.hub.core.scheduler.Job;
import cafe.jeffrey.hub.core.scheduler.JobLocks;
import cafe.jeffrey.hub.core.scheduler.JobRegistry;
import cafe.jeffrey.hub.core.scheduler.ManualJobRunner;
import cafe.jeffrey.hub.core.scheduler.PeriodicalScheduler;
import cafe.jeffrey.hub.core.scheduler.job.DeletedProjectsCleanerJob;
import cafe.jeffrey.hub.core.scheduler.job.ExpiredInstanceCleanerJob;
import cafe.jeffrey.hub.core.config.ScopedConfigManager;
import cafe.jeffrey.hub.core.scheduler.job.ScopedConfigSynchronizerJob;
import cafe.jeffrey.hub.core.scheduler.job.ProjectInstanceSessionCleanerJob;
import cafe.jeffrey.hub.core.scheduler.job.ProjectStorageQuotaCleanerJob;
import cafe.jeffrey.hub.core.scheduler.job.RepositoryCompressionProjectJob;
import cafe.jeffrey.hub.core.scheduler.job.SessionFinishedDetectorProjectJob;
import cafe.jeffrey.hub.core.scheduler.job.StorageOverviewRefresherJob;
import cafe.jeffrey.hub.core.scheduler.job.TempDirectoryCleanerJob;
import cafe.jeffrey.hub.core.scheduler.job.WorkspaceReconcilerJob;
import cafe.jeffrey.hub.core.workspace.reconcile.WorkspaceReconciler;
import cafe.jeffrey.hub.model.job.JobType;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.List;

/**
 * The scheduler and every job it runs. Each job's enabled flag, period and params come from
 * {@link SchedulerJobsProperties}, which merges the built-in {@code scheduler-defaults.properties}
 * with overrides in {@code application.properties}; a disabled job is still constructed — the
 * registry lists it — but never scheduled. {@code jeffrey.hub.scheduler.enabled=false} keeps
 * the whole scheduler from starting, for a hub run only to serve what is already on the volume.
 */
@Configuration
public class SchedulerConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(SchedulerConfiguration.class);

    private final WorkspacesManager workspacesManager;
    private final SchedulerJobsProperties properties;

    public SchedulerConfiguration(WorkspacesManager workspacesManager, SchedulerJobsProperties properties) {
        this.workspacesManager = workspacesManager;
        this.properties = properties;
    }

    private JobConfig config(JobType jobType) {
        return properties.forType(jobType);
    }

    // ========== Scheduler ==========

    @Bean
    public JobLocks jobLocks() {
        return new JobLocks();
    }

    @Bean(destroyMethod = "close")
    public PeriodicalScheduler scheduler(List<Job> jobs, JobLocks jobLocks) {
        List<Job> enabled = jobs.stream()
                .filter(job -> {
                    boolean on = config(job.jobType()).enabled();
                    if (!on) {
                        LOG.info("Scheduler job disabled, skipping registration: job_type={}", job.jobType());
                    }
                    return on;
                })
                .toList();
        LOG.info("Registered scheduler jobs: enabled={} total={} fan_out_pool_size={}",
                enabled.size(), jobs.size(), properties.getFanOutPoolSize());
        return new PeriodicalScheduler(enabled, properties.getFanOutPoolSize(), jobLocks);
    }

    @Bean
    @ConditionalOnProperty(name = "jeffrey.hub.scheduler.enabled", havingValue = "true", matchIfMissing = true)
    public SchedulerInitializer schedulerInitializer(PeriodicalScheduler scheduler) {
        return new SchedulerInitializer(scheduler);
    }

    @Bean
    public ManualJobRunner manualJobRunner(List<Job> jobs, JobLocks jobLocks, Clock clock) {
        return new ManualJobRunner(jobs, jobLocks, clock);
    }

    @Bean
    public JobRegistry jobRegistry(ManualJobRunner manualJobRunner) {
        return new JobRegistry(properties, manualJobRunner.supportedTypes());
    }

    // ========== GLOBAL jobs ==========

    @Bean
    public WorkspaceReconcilerJob workspaceReconcilerJob(
            WorkspaceReconciler workspaceReconciler,
            HubJeffreyDirs jeffreyDirs,
            DefaultWorkspaceProperties defaultWorkspaceProperties,
            WorkspacesProperties workspacesProperties,
            Clock clock) {
        return new WorkspaceReconcilerJob(
                workspacesManager,
                workspaceReconciler,
                jeffreyDirs,
                defaultWorkspaceProperties,
                workspacesProperties,
                clock,
                config(JobType.WORKSPACE_RECONCILER).period());
    }

    @Bean
    public TempDirectoryCleanerJob tempDirectoryCleanerJob(HubJeffreyDirs jeffreyDirs, Clock clock) {
        return new TempDirectoryCleanerJob(jeffreyDirs.temp(), clock, config(JobType.TEMP_DIRECTORY_CLEANER));
    }

    @Bean
    public DeletedProjectsCleanerJob deletedProjectsCleanerJob(HubPlatformRepositories platformRepositories, Clock clock) {
        return new DeletedProjectsCleanerJob(
                platformRepositories.newProjectsRepository(), clock, config(JobType.DELETED_PROJECTS_CLEANER));
    }

    @Bean
    public StorageOverviewRefresherJob storageOverviewRefresherJob(StorageOverviewCache storageOverviewCache) {
        return new StorageOverviewRefresherJob(storageOverviewCache, config(JobType.STORAGE_OVERVIEW_REFRESHER));
    }

    // ========== WORKSPACE fan-out ==========

    @Bean
    public ScopedConfigSynchronizerJob scopedConfigSynchronizerJob(ScopedConfigManager configManager) {
        return new ScopedConfigSynchronizerJob(
                workspacesManager,
                config(JobType.SCOPED_CONFIG_SYNCHRONIZER),
                configManager);
    }

    // ========== PROJECT fan-out ==========

    @Bean
    public ProjectInstanceSessionCleanerJob projectInstanceSessionCleanerJob(Clock clock) {
        return new ProjectInstanceSessionCleanerJob(
                workspacesManager, config(JobType.PROJECT_INSTANCE_SESSION_CLEANER), clock);
    }

    @Bean
    public ProjectStorageQuotaCleanerJob projectStorageQuotaCleanerJob() {
        return new ProjectStorageQuotaCleanerJob(workspacesManager, config(JobType.PROJECT_STORAGE_QUOTA_CLEANER));
    }

    @Bean
    public RepositoryCompressionProjectJob repositoryCompressionProjectJob() {
        return new RepositoryCompressionProjectJob(workspacesManager, config(JobType.REPOSITORY_JFR_COMPRESSION));
    }

    @Bean
    public ExpiredInstanceCleanerJob expiredInstanceCleanerJob(Clock clock) {
        return new ExpiredInstanceCleanerJob(workspacesManager, config(JobType.EXPIRED_INSTANCE_CLEANER), clock);
    }

    @Bean
    public SessionFinishedDetectorProjectJob sessionFinishedDetectorProjectJob(
            HubJeffreyDirs jeffreyDirs,
            HubPlatformRepositories platformRepositories,
            SessionFinisher sessionFinisher) {
        return new SessionFinishedDetectorProjectJob(
                workspacesManager,
                config(JobType.SESSION_FINISHED_DETECTOR),
                jeffreyDirs,
                platformRepositories,
                sessionFinisher);
    }
}
