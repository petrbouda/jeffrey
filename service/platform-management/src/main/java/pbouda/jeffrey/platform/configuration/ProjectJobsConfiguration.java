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

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pbouda.jeffrey.platform.manager.workspace.CompositeWorkspacesManager;
import pbouda.jeffrey.platform.manager.workspace.LiveWorkspacesManager;
import pbouda.jeffrey.platform.project.repository.RemoteRepositoryStorage;
import pbouda.jeffrey.platform.scheduler.JobContext;
import pbouda.jeffrey.provider.api.repository.Repositories;
import pbouda.jeffrey.platform.scheduler.PeriodicalScheduler;
import pbouda.jeffrey.platform.scheduler.Scheduler;
import pbouda.jeffrey.platform.scheduler.job.*;
import pbouda.jeffrey.platform.scheduler.job.descriptor.JobDescriptorFactory;
import pbouda.jeffrey.storage.recording.api.RecordingStorage;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/**
 * Configuration for PROJECT-level scheduler jobs.
 * These jobs operate on individual projects and query project-level schedulers.
 */
@Configuration
public class ProjectJobsConfiguration {

    public static final String REPOSITORY_COMPRESSION_TRIGGER = "REPOSITORY_COMPRESSION_TRIGGER";

    private static final String PROJECT_SCHEDULER = "PROJECT_SCHEDULER";

    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);

    private final RemoteRepositoryStorage.Factory repositoryStorageFactory;
    private final LiveWorkspacesManager liveWorkspacesManager;
    private final JobDescriptorFactory jobDescriptorFactory;
    private final Duration defaultPeriod;

    public ProjectJobsConfiguration(
            LiveWorkspacesManager liveWorkspacesManager,
            RemoteRepositoryStorage.Factory repositoryStorageFactory,
            JobDescriptorFactory jobDescriptorFactory,
            @Value("${jeffrey.job.default.period:}") Duration defaultPeriod) {

        this.liveWorkspacesManager = liveWorkspacesManager;
        this.jobDescriptorFactory = jobDescriptorFactory;
        this.defaultPeriod = defaultPeriod == null ? ONE_MINUTE : defaultPeriod;
        this.repositoryStorageFactory = repositoryStorageFactory;
    }

    @Bean(name = PROJECT_SCHEDULER, destroyMethod = "close")
    public Scheduler projectScheduler(List<ProjectJob<?>> jobs) {
        return new PeriodicalScheduler(jobs, Duration.ofSeconds(5));
    }

    @Bean
    public RepositorySessionCleanerProjectJob repositorySessionCleanerProjectJob(
            @Value("${jeffrey.job.repository-session-cleaner.period:}") Duration jobPeriod) {
        return new RepositorySessionCleanerProjectJob(
                liveWorkspacesManager,
                repositoryStorageFactory,
                jobDescriptorFactory,
                jobPeriod == null ? defaultPeriod : jobPeriod);
    }

    @Bean
    public RepositoryRecordingCleanerProjectJob repositoryRecordingCleanerProjectJob(
            @Value("${jeffrey.job.repository-recording-cleaner.period:}") Duration jobPeriod) {
        return new RepositoryRecordingCleanerProjectJob(
                liveWorkspacesManager,
                repositoryStorageFactory,
                jobDescriptorFactory,
                jobPeriod == null ? defaultPeriod : jobPeriod);
    }

    @Bean
    public RepositoryCompressionProjectJob repositoryCompressionProjectJob(
            SessionFileCompressor sessionFileCompressor,
            @Value("${jeffrey.job.repository-compression.period:}") Duration jobPeriod) {
        return new RepositoryCompressionProjectJob(
                liveWorkspacesManager,
                repositoryStorageFactory,
                jobDescriptorFactory,
                sessionFileCompressor,
                jobPeriod == null ? defaultPeriod : jobPeriod);
    }

    @Bean
    public RecordingIntervalGeneratorProjectJob recordingGeneratorProjectJob(
            @Value("${jeffrey.job.recording-generator.period:}") Duration jobPeriod) {
        return new RecordingIntervalGeneratorProjectJob(
                liveWorkspacesManager,
                repositoryStorageFactory,
                jobDescriptorFactory,
                jobPeriod == null ? defaultPeriod : jobPeriod);
    }

    @Bean
    public ProjectRecordingStorageSynchronizerJob projectRecordingStorageSynchronizerJob(
            CompositeWorkspacesManager compositeWorkspacesManager,
            Repositories repositories,
            RecordingStorage recordingStorage,
            @Value("${jeffrey.job.project-recording-storage-synchronizer.period:}") Duration jobPeriod) {

        return new ProjectRecordingStorageSynchronizerJob(
                compositeWorkspacesManager,
                jobDescriptorFactory,
                repositories,
                recordingStorage,
                jobPeriod == null ? defaultPeriod : jobPeriod);
    }

    /**
     * Compression trigger that accepts an optional sessionId parameter.
     * When sessionId is provided, only that specific session is compressed.
     * When sessionId is null, the default behavior (ACTIVE + latest FINISHED sessions) is used.
     */
    @Bean(REPOSITORY_COMPRESSION_TRIGGER)
    public Consumer<String> repositoryCompressionTrigger(
            @Qualifier(PROJECT_SCHEDULER) Scheduler scheduler,
            RepositoryCompressionProjectJob repositoryCompressionJob) {

        return sessionId -> {
            JobContext context = sessionId != null
                    ? JobContext.of(RepositoryCompressionProjectJob.PARAM_SESSION_ID, sessionId)
                    : JobContext.EMPTY;
            scheduler.submitAndWait(repositoryCompressionJob, context);
        };
    }
}
