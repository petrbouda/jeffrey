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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pbouda.jeffrey.platform.manager.workspace.CompositeWorkspacesManager;
import pbouda.jeffrey.platform.manager.workspace.LiveWorkspacesManager;
import pbouda.jeffrey.platform.project.repository.RepositoryStorage;
import pbouda.jeffrey.platform.project.repository.SessionFinishEventEmitter;
import pbouda.jeffrey.platform.queue.PersistentQueue;
import pbouda.jeffrey.platform.streaming.HeartbeatReplayReader;
import pbouda.jeffrey.platform.streaming.SessionFinisher;
import pbouda.jeffrey.platform.scheduler.PeriodicalScheduler;
import pbouda.jeffrey.platform.scheduler.Scheduler;
import pbouda.jeffrey.platform.scheduler.job.*;
import pbouda.jeffrey.platform.scheduler.job.descriptor.JobDescriptorFactory;
import pbouda.jeffrey.provider.platform.repository.PlatformRepositories;
import pbouda.jeffrey.shared.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.storage.recording.api.RecordingStorage;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Configuration for PROJECT-level scheduler jobs.
 * These jobs operate on individual projects and query project-level schedulers.
 */
@Configuration
public class ProjectJobsConfiguration {

    private static final String PROJECT_SCHEDULER = "PROJECT_SCHEDULER";

    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);

    private final RepositoryStorage.Factory repositoryStorageFactory;
    private final LiveWorkspacesManager liveWorkspacesManager;
    private final JobDescriptorFactory jobDescriptorFactory;
    private final Duration defaultPeriod;

    public ProjectJobsConfiguration(
            LiveWorkspacesManager liveWorkspacesManager,
            RepositoryStorage.Factory repositoryStorageFactory,
            JobDescriptorFactory jobDescriptorFactory,
            @Value("${jeffrey.job.default.period:}") Duration defaultPeriod) {

        this.liveWorkspacesManager = liveWorkspacesManager;
        this.jobDescriptorFactory = jobDescriptorFactory;
        this.defaultPeriod = defaultPeriod == null ? ONE_MINUTE : defaultPeriod;
        this.repositoryStorageFactory = repositoryStorageFactory;
    }

    @Bean(name = PROJECT_SCHEDULER, destroyMethod = "close")
    public Scheduler projectScheduler(List<ProjectJob<?>> jobs) {
        return new PeriodicalScheduler(jobs);
    }

    @Bean
    public ProjectInstanceSessionCleanerJob projectInstanceSessionCleanerJob(
            @Value("${jeffrey.job.project-instance-session-cleaner.period:}") Duration jobPeriod) {
        return new ProjectInstanceSessionCleanerJob(
                liveWorkspacesManager,
                repositoryStorageFactory,
                jobDescriptorFactory,
                jobPeriod == null ? defaultPeriod : jobPeriod);
    }

    @Bean
    public ProjectInstanceRecordingCleanerJob projectInstanceRecordingCleanerJob(
            @Value("${jeffrey.job.project-instance-recording-cleaner.period:}") Duration jobPeriod) {
        return new ProjectInstanceRecordingCleanerJob(
                liveWorkspacesManager,
                repositoryStorageFactory,
                jobDescriptorFactory,
                jobPeriod == null ? defaultPeriod : jobPeriod);
    }

    @Bean
    public RepositoryCompressionProjectJob repositoryCompressionProjectJob(
            @Value("${jeffrey.job.repository-compression.period:}") Duration jobPeriod) {
        return new RepositoryCompressionProjectJob(
                liveWorkspacesManager,
                repositoryStorageFactory,
                jobDescriptorFactory,
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
            PlatformRepositories platformRepositories,
            RecordingStorage recordingStorage,
            @Value("${jeffrey.job.project-recording-storage-synchronizer.period:}") Duration jobPeriod) {

        return new ProjectRecordingStorageSynchronizerJob(
                compositeWorkspacesManager,
                jobDescriptorFactory,
                platformRepositories,
                recordingStorage,
                jobPeriod == null ? defaultPeriod : jobPeriod);
    }

    @Bean
    public HeartbeatReplayReader heartbeatReplayReader(Clock clock) {
        return new HeartbeatReplayReader(clock);
    }

    @Bean
    public SessionFinishEventEmitter sessionFinishEventEmitter(
            Clock clock,
            PersistentQueue<WorkspaceEvent> workspaceEventQueue) {

        return new SessionFinishEventEmitter(clock, workspaceEventQueue);
    }

    @Bean
    public SessionFinisher sessionFinisher(
            Clock clock,
            SessionFinishEventEmitter sessionFinishEventEmitter,
            HeartbeatReplayReader heartbeatReplayReader) {

        return new SessionFinisher(clock, sessionFinishEventEmitter, heartbeatReplayReader);
    }

    @Bean
    public SessionFinishedDetectorProjectJob sessionFinishedDetectorProjectJob(
            Clock clock,
            JeffreyDirs jeffreyDirs,
            PlatformRepositories platformRepositories,
            SessionFinisher sessionFinisher,
            @Value("${jeffrey.job.session-finished-detector.period:10s}") Duration jobPeriod,
            @Value("${jeffrey.platform.streaming.heartbeat-timeout:10s}") Duration heartbeatTimeout) {

        return new SessionFinishedDetectorProjectJob(
                liveWorkspacesManager,
                repositoryStorageFactory,
                jobDescriptorFactory,
                jobPeriod,
                heartbeatTimeout,
                clock,
                jeffreyDirs,
                platformRepositories,
                sessionFinisher);
    }
}
