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
import pbouda.jeffrey.platform.configuration.properties.JobProperties;
import pbouda.jeffrey.platform.manager.workspace.CompositeWorkspacesManager;
import pbouda.jeffrey.platform.manager.workspace.LiveWorkspacesManager;
import pbouda.jeffrey.platform.project.repository.RepositoryStorage;
import pbouda.jeffrey.platform.project.repository.SessionFinishEventEmitter;
import pbouda.jeffrey.platform.queue.PersistentQueue;
import pbouda.jeffrey.platform.scheduler.PeriodicalScheduler;
import pbouda.jeffrey.platform.scheduler.Scheduler;
import pbouda.jeffrey.platform.scheduler.job.*;
import pbouda.jeffrey.platform.scheduler.job.descriptor.JobDescriptorFactory;
import pbouda.jeffrey.platform.streaming.FileHeartbeatReader;
import pbouda.jeffrey.platform.streaming.JfrStreamingConsumerManager;
import pbouda.jeffrey.platform.streaming.SessionFinisher;
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

    private final RepositoryStorage.Factory repositoryStorageFactory;
    private final LiveWorkspacesManager liveWorkspacesManager;
    private final JobDescriptorFactory jobDescriptorFactory;
    private final JobProperties jobProperties;

    public ProjectJobsConfiguration(
            LiveWorkspacesManager liveWorkspacesManager,
            RepositoryStorage.Factory repositoryStorageFactory,
            JobDescriptorFactory jobDescriptorFactory,
            JobProperties jobProperties) {

        this.liveWorkspacesManager = liveWorkspacesManager;
        this.jobDescriptorFactory = jobDescriptorFactory;
        this.repositoryStorageFactory = repositoryStorageFactory;
        this.jobProperties = jobProperties;
    }

    @Bean(name = PROJECT_SCHEDULER, destroyMethod = "close")
    public Scheduler projectScheduler(List<ProjectJob<?>> jobs) {
        return new PeriodicalScheduler(jobs);
    }

    @Bean
    public ProjectInstanceSessionCleanerJob projectInstanceSessionCleanerJob(
            Clock clock,
            PlatformRepositories platformRepositories) {
        return new ProjectInstanceSessionCleanerJob(
                liveWorkspacesManager,
                repositoryStorageFactory,
                jobDescriptorFactory,
                jobProperties.resolvePeriod("project-instance-session-cleaner"),
                clock,
                platformRepositories);
    }

    @Bean
    public ProjectInstanceRecordingCleanerJob projectInstanceRecordingCleanerJob() {
        return new ProjectInstanceRecordingCleanerJob(
                liveWorkspacesManager,
                repositoryStorageFactory,
                jobDescriptorFactory,
                jobProperties.resolvePeriod("project-instance-recording-cleaner"));
    }

    @Bean
    public RepositoryCompressionProjectJob repositoryCompressionProjectJob() {
        return new RepositoryCompressionProjectJob(
                liveWorkspacesManager,
                repositoryStorageFactory,
                jobDescriptorFactory,
                jobProperties.resolvePeriod("repository-compression"));
    }

    @Bean
    public RecordingIntervalGeneratorProjectJob recordingGeneratorProjectJob() {
        return new RecordingIntervalGeneratorProjectJob(
                liveWorkspacesManager,
                repositoryStorageFactory,
                jobDescriptorFactory,
                jobProperties.resolvePeriod("recording-generator"));
    }

    @Bean
    public ProjectRecordingStorageSynchronizerJob projectRecordingStorageSynchronizerJob(
            CompositeWorkspacesManager compositeWorkspacesManager,
            PlatformRepositories platformRepositories,
            RecordingStorage recordingStorage) {

        return new ProjectRecordingStorageSynchronizerJob(
                compositeWorkspacesManager,
                jobDescriptorFactory,
                platformRepositories,
                recordingStorage,
                jobProperties.resolvePeriod("project-recording-storage-synchronizer"));
    }

    @Bean
    public ExpiredInstanceCleanerJob expiredInstanceCleanerJob(
            Clock clock,
            PlatformRepositories platformRepositories) {
        return new ExpiredInstanceCleanerJob(
                liveWorkspacesManager,
                jobDescriptorFactory,
                jobProperties.resolvePeriod("expired-instance-cleaner", Duration.ofHours(1)),
                clock,
                platformRepositories);
    }

    @Bean
    public FileHeartbeatReader fileHeartbeatReader() {
        return new FileHeartbeatReader();
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
            FileHeartbeatReader fileHeartbeatReader,
            JfrStreamingConsumerManager jfrStreamingConsumerManager,
            PlatformRepositories platformRepositories) {

        return new SessionFinisher(clock, sessionFinishEventEmitter, fileHeartbeatReader, jfrStreamingConsumerManager, platformRepositories);
    }

    @Bean
    public SessionFinishedDetectorProjectJob sessionFinishedDetectorProjectJob(
            Clock clock,
            JeffreyDirs jeffreyDirs,
            PlatformRepositories platformRepositories,
            SessionFinisher sessionFinisher,
            @Value("${jeffrey.platform.streaming.heartbeat-timeout:10s}") Duration heartbeatTimeout) {

        return new SessionFinishedDetectorProjectJob(
                liveWorkspacesManager,
                repositoryStorageFactory,
                jobDescriptorFactory,
                jobProperties.resolvePeriod("session-finished-detector", Duration.ofSeconds(10)),
                heartbeatTimeout,
                clock,
                jeffreyDirs,
                platformRepositories,
                sessionFinisher);
    }
}
