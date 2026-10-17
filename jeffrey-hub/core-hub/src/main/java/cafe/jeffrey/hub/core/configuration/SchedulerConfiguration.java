/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cafe.jeffrey.hub.core.configuration;

import cafe.jeffrey.hub.core.appinitializer.SchedulerInitializer;
import cafe.jeffrey.hub.core.configuration.properties.SchedulerJobsProperties;
import cafe.jeffrey.hub.core.configuration.properties.SchedulerJobsProperties.JobConfig;
import cafe.jeffrey.hub.core.manager.storage.StorageOverviewCache;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.core.scheduler.Job;
import cafe.jeffrey.hub.core.scheduler.JobLocks;
import cafe.jeffrey.hub.core.scheduler.JobRegistry;
import cafe.jeffrey.hub.core.scheduler.ManualJobRunner;
import cafe.jeffrey.hub.core.scheduler.PeriodicalScheduler;
import cafe.jeffrey.hub.core.scheduler.job.ProjectRetentionJob;
import cafe.jeffrey.hub.core.scheduler.job.RepositoryCompressionProjectJob;
import cafe.jeffrey.hub.model.job.JobType;
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

    // ========== PROJECT fan-out ==========

    @Bean
    public ProjectRetentionJob projectRetentionJob(StorageOverviewCache storageOverviewCache, Clock clock) {
        return new ProjectRetentionJob(
                workspacesManager, config(JobType.PROJECT_RETENTION), storageOverviewCache, clock);
    }

    @Bean
    public RepositoryCompressionProjectJob repositoryCompressionProjectJob() {
        return new RepositoryCompressionProjectJob(workspacesManager, config(JobType.REPOSITORY_JFR_COMPRESSION));
    }
}
