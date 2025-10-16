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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import pbouda.jeffrey.appinitializer.GlobalJobsInitializer;
import pbouda.jeffrey.appinitializer.JfrEventListenerInitializer;
import pbouda.jeffrey.appinitializer.SchedulerInitializer;
import pbouda.jeffrey.manager.SchedulerManager;
import pbouda.jeffrey.manager.workspace.LocalWorkspacesManager;
import pbouda.jeffrey.project.repository.RemoteRepositoryStorage;
import pbouda.jeffrey.provider.api.repository.Repositories;
import pbouda.jeffrey.scheduler.PeriodicalScheduler;
import pbouda.jeffrey.scheduler.Scheduler;
import pbouda.jeffrey.scheduler.job.Job;
import pbouda.jeffrey.scheduler.job.ProjectsSynchronizerJob;
import pbouda.jeffrey.scheduler.job.RecordingIntervalGeneratorProjectJob;
import pbouda.jeffrey.scheduler.job.RecordingStorageSynchronizerJob;
import pbouda.jeffrey.scheduler.job.RepositoryRecordingCleanerProjectJob;
import pbouda.jeffrey.scheduler.job.RepositorySessionCleanerProjectJob;
import pbouda.jeffrey.scheduler.job.WorkspaceEventsReplicatorJob;
import pbouda.jeffrey.scheduler.job.descriptor.JobDescriptorFactory;
import pbouda.jeffrey.storage.recording.api.RecordingStorage;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static pbouda.jeffrey.configuration.AppConfiguration.GLOBAL_SCHEDULER_MANAGER_BEAN;

@Configuration
@Import(ProfileFactoriesConfiguration.class)
public class JobsConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(JobsConfiguration.class);

    public static final String PROJECTS_SYNCHRONIZER_JOB = "PROJECTS_SYNCHRONIZER";

    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);

    private final RemoteRepositoryStorage.Factory repositoryStorageFactory;
    private final LocalWorkspacesManager localWorkspacesManager;
    private final JobDescriptorFactory jobDescriptorFactory;
    private final SchedulerManager schedulerManager;
    private final Duration defaultPeriod;

    public JobsConfiguration(
            LocalWorkspacesManager localWorkspacesManager,
            RemoteRepositoryStorage.Factory repositoryStorageFactory,
            JobDescriptorFactory jobDescriptorFactory,
            @Qualifier(GLOBAL_SCHEDULER_MANAGER_BEAN) SchedulerManager schedulerManager,
            @Value("${jeffrey.job.default.period:}") Duration defaultPeriod) {

        this.localWorkspacesManager = localWorkspacesManager;
        this.jobDescriptorFactory = jobDescriptorFactory;
        this.schedulerManager = schedulerManager;
        this.defaultPeriod = defaultPeriod == null ? ONE_MINUTE : defaultPeriod;
        this.repositoryStorageFactory = repositoryStorageFactory;
    }

    @Bean(destroyMethod = "close")
    public Scheduler jobScheduler(List<Job> jobs) {
        return new PeriodicalScheduler(jobs);
    }

    @Bean
    @ConditionalOnProperty(name = "jeffrey.job.scheduler.enabled", havingValue = "true", matchIfMissing = true)
    public SchedulerInitializer schedulerInitializer(Scheduler scheduler) {
        return new SchedulerInitializer(scheduler);
    }

    @Bean
    @ConditionalOnProperty(name = "jeffrey.logging.jfr-events.application.enabled", havingValue = "true")
    public JfrEventListenerInitializer jfrEventListenerInitializer(
            @Value("${jeffrey.logging.jfr-events.application.threshold:}") Duration threshold) {
        Duration resolvedThreshold = threshold == null || !threshold.isPositive() ? null : threshold;
        return new JfrEventListenerInitializer(resolvedThreshold);
    }

    @Bean
    public GlobalJobsInitializer globalJobsInitializer() {
        return new GlobalJobsInitializer(schedulerManager);
    }

    @Bean
    public Job repositorySesssionCleanerProjectJob(
            @Value("${jeffrey.job.repository-session-cleaner.period:}") Duration jobPeriod) {
        return new RepositorySessionCleanerProjectJob(
                localWorkspacesManager,
                schedulerManager,
                repositoryStorageFactory,
                jobDescriptorFactory,
                jobPeriod == null ? defaultPeriod : jobPeriod);
    }

    @Bean
    public Job repositoryRecordingCleanerProjectJob(
            @Value("${jeffrey.job.repository-recording-cleaner.period:}") Duration jobPeriod) {
        return new RepositoryRecordingCleanerProjectJob(
                localWorkspacesManager,
                schedulerManager,
                repositoryStorageFactory,
                jobDescriptorFactory,
                jobPeriod == null ? defaultPeriod : jobPeriod);
    }

    @Bean
    public Job recordingGeneratorProjectJob(@Value("${jeffrey.job.recording-generator.period:}") Duration jobPeriod) {
        return new RecordingIntervalGeneratorProjectJob(
                localWorkspacesManager,
                schedulerManager,
                repositoryStorageFactory,
                jobDescriptorFactory,
                jobPeriod == null ? defaultPeriod : jobPeriod);
    }

    @Bean
    public Job recordingStorageSynchronizerJob(
            Repositories repositories,
            RecordingStorage recordingStorage,
            @Value("${jeffrey.job.recording-storage-synchronizer.period:}") Duration jobPeriod) {

        return new RecordingStorageSynchronizerJob(
                repositories,
                recordingStorage,
                jobPeriod == null ? defaultPeriod : jobPeriod);
    }

    @Bean(name = PROJECTS_SYNCHRONIZER_JOB)
    public Job projectsSynchronizerJob(@Value("${jeffrey.job.projects-synchronizer.period:}") Duration jobPeriod) {
        return new ProjectsSynchronizerJob(
                localWorkspacesManager,
                schedulerManager,
                jobDescriptorFactory,
                jobPeriod == null ? defaultPeriod : jobPeriod);
    }

    @Bean
    public Job workspaceEventsReplicatorJob(
            ObjectFactory<Scheduler> scheduler,
            Clock clock,
            @Qualifier(PROJECTS_SYNCHRONIZER_JOB) Job projectsSynchronizerJob,
            @Value("${jeffrey.job.workspace-events-replicator.period:}") Duration jobPeriod) {

        Runnable migrationCallback = () -> {
            LOG.info("Executing migration callback after workspace events replication, " +
                     "triggering projects synchronizer job.");
            scheduler.getObject().executeNow(projectsSynchronizerJob);
        };

        return new WorkspaceEventsReplicatorJob(
                localWorkspacesManager,
                schedulerManager,
                jobDescriptorFactory,
                jobPeriod == null ? defaultPeriod : jobPeriod,
                clock,
                migrationCallback);
    }
}
