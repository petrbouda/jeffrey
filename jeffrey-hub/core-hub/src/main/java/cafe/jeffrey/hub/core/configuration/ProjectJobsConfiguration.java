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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import cafe.jeffrey.hub.core.HubJeffreyDirs;
import cafe.jeffrey.hub.core.configuration.properties.SchedulerJobsProperties;
import cafe.jeffrey.hub.core.configuration.properties.SchedulerJobsProperties.JobConfig;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.core.project.repository.InstanceLifecycleEventEmitter;
import cafe.jeffrey.hub.core.project.repository.SessionFinishEventEmitter;
import cafe.jeffrey.hub.core.scheduler.job.*;
import cafe.jeffrey.hub.core.scheduler.job.descriptor.ExpiredInstanceCleanerJobDescriptor;
import cafe.jeffrey.hub.core.scheduler.job.descriptor.ProjectInstanceRecordingCleanerJobDescriptor;
import cafe.jeffrey.hub.core.scheduler.job.descriptor.ProjectInstanceSessionCleanerJobDescriptor;
import cafe.jeffrey.hub.core.scheduler.job.descriptor.ProjectStorageQuotaCleanerJobDescriptor;
import cafe.jeffrey.hub.core.scheduler.job.descriptor.SessionFileDetectorProjectJobDescriptor;
import cafe.jeffrey.hub.core.streaming.FileHeartbeatReader;
import cafe.jeffrey.hub.core.streaming.SessionFinisher;
import cafe.jeffrey.hub.core.workspace.WorkspaceEventPublisher;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
import cafe.jeffrey.shared.common.model.job.JobType;

import java.time.Clock;
import java.time.Duration;

/**
 * Configuration for PROJECT-level scheduler jobs. Each {@code @Bean} resolves
 * its configuration from {@link SchedulerJobsProperties} and constructs the
 * job; the global scheduler bean filters out jobs whose
 * {@code .enabled} property is false.
 */
@Configuration
public class ProjectJobsConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectJobsConfiguration.class);

    private static final String WORKSPACE_EVENTS_RETENTION_PARAM = "queue-events-retention";

    private final RepositoryStorage.Factory repositoryStorageFactory;
    private final WorkspacesManager workspacesManager;
    private final SchedulerJobsProperties schedulerJobsProperties;

    public ProjectJobsConfiguration(
            WorkspacesManager workspacesManager,
            RepositoryStorage.Factory repositoryStorageFactory,
            SchedulerJobsProperties schedulerJobsProperties) {

        this.workspacesManager = workspacesManager;
        this.repositoryStorageFactory = repositoryStorageFactory;
        this.schedulerJobsProperties = schedulerJobsProperties;
    }

    @Bean
    public ProjectInstanceSessionCleanerJob projectInstanceSessionCleanerJob(
            Clock clock,
            HubPlatformRepositories platformRepositories) {
        SchedulerJobsProperties.JobConfig config =
                schedulerJobsProperties.forType(JobType.PROJECT_INSTANCE_SESSION_CLEANER);
        return new ProjectInstanceSessionCleanerJob(
                workspacesManager,
                repositoryStorageFactory,
                ProjectInstanceSessionCleanerJobDescriptor.of(config.params()),
                config.period(),
                clock,
                platformRepositories);
    }

    @Bean
    public ProjectInstanceRecordingCleanerJob projectInstanceRecordingCleanerJob(Clock clock) {
        SchedulerJobsProperties.JobConfig config =
                schedulerJobsProperties.forType(JobType.PROJECT_INSTANCE_RECORDING_CLEANER);
        return new ProjectInstanceRecordingCleanerJob(
                workspacesManager,
                repositoryStorageFactory,
                ProjectInstanceRecordingCleanerJobDescriptor.of(config.params()),
                config.period(),
                clock);
    }

    @Bean
    public ProjectStorageQuotaCleanerJob projectStorageQuotaCleanerJob() {
        SchedulerJobsProperties.JobConfig config =
                schedulerJobsProperties.forType(JobType.PROJECT_STORAGE_QUOTA_CLEANER);
        return new ProjectStorageQuotaCleanerJob(
                workspacesManager,
                repositoryStorageFactory,
                ProjectStorageQuotaCleanerJobDescriptor.of(config.params()),
                config.period());
    }

    @Bean
    public RepositoryCompressionProjectJob repositoryCompressionProjectJob() {
        return new RepositoryCompressionProjectJob(
                workspacesManager,
                repositoryStorageFactory,
                schedulerJobsProperties.forType(JobType.REPOSITORY_JFR_COMPRESSION).period());
    }

    @Bean
    public ExpiredInstanceCleanerJob expiredInstanceCleanerJob(
            Clock clock,
            HubPlatformRepositories platformRepositories) {
        SchedulerJobsProperties.JobConfig config =
                schedulerJobsProperties.forType(JobType.EXPIRED_INSTANCE_CLEANER);
        return new ExpiredInstanceCleanerJob(
                workspacesManager,
                repositoryStorageFactory,
                ExpiredInstanceCleanerJobDescriptor.of(config.params()),
                config.period(),
                clock,
                platformRepositories);
    }

    @Bean
    public SessionFinishEventEmitter sessionFinishEventEmitter(
            Clock clock,
            WorkspaceEventPublisher workspaceEventPublisher) {

        return new SessionFinishEventEmitter(clock, workspaceEventPublisher);
    }

    @Bean
    public InstanceLifecycleEventEmitter instanceLifecycleEventEmitter(
            Clock clock,
            WorkspaceEventPublisher workspaceEventPublisher) {

        return new InstanceLifecycleEventEmitter(clock, workspaceEventPublisher);
    }

    @Bean
    public SessionFinisher sessionFinisher(
            Clock clock,
            SessionFinishEventEmitter sessionFinishEventEmitter,
            InstanceLifecycleEventEmitter instanceLifecycleEventEmitter,
            FileHeartbeatReader fileHeartbeatReader,
            HubPlatformRepositories platformRepositories) {

        return new SessionFinisher(clock, sessionFinishEventEmitter, instanceLifecycleEventEmitter,
                fileHeartbeatReader, platformRepositories);
    }

    @Bean
    public SessionFinishedDetectorProjectJob sessionFinishedDetectorProjectJob(
            Clock clock,
            HubJeffreyDirs jeffreyDirs,
            HubPlatformRepositories platformRepositories,
            SessionFinisher sessionFinisher,
            @Value("${jeffrey.hub.platform.streaming.heartbeat-timeout:10s}") Duration heartbeatTimeout) {

        return new SessionFinishedDetectorProjectJob(
                workspacesManager,
                repositoryStorageFactory,
                schedulerJobsProperties.forType(JobType.SESSION_FINISHED_DETECTOR).period(),
                heartbeatTimeout,
                clock,
                jeffreyDirs,
                platformRepositories,
                sessionFinisher);
    }

    @Bean
    public SessionFileDetectorProjectJob sessionFileDetectorProjectJob(
            Clock clock,
            WorkspaceEventPublisher workspaceEventPublisher) {

        JobConfig config = schedulerJobsProperties.forType(JobType.SESSION_FILE_DETECTOR);
        SessionFileDetectorProjectJobDescriptor descriptor =
                SessionFileDetectorProjectJobDescriptor.of(config.params());

        verifyFileAgeStaysWithinEventRetention(descriptor);

        return new SessionFileDetectorProjectJob(
                workspacesManager,
                repositoryStorageFactory,
                descriptor,
                config.period(),
                clock,
                workspaceEventPublisher);
    }

    /**
     * The file detector is stateless and leans on the event queue's dedup index for idempotency,
     * which only holds while the dedup row still exists. Once {@code max-file-age} reaches
     * {@code queue-events-retention}, a file can outlive its own dedup row and gets announced
     * again — silently, as duplicate events rather than an error. The two properties live in
     * different job configs, so nothing else would catch the mistake.
     */
    private void verifyFileAgeStaysWithinEventRetention(SessionFileDetectorProjectJobDescriptor descriptor) {
        JobConfig cleanerConfig = schedulerJobsProperties.forType(JobType.WORKSPACE_EVENTS_CLEANER);
        Duration eventRetention;
        try {
            eventRetention = cleanerConfig.durationParam(WORKSPACE_EVENTS_RETENTION_PARAM);
        } catch (IllegalArgumentException e) {
            LOG.warn("Cannot verify session-file-detector max-file-age against workspace event retention: reason={}",
                    e.getMessage());
            return;
        }

        if (descriptor.maxFileAge().compareTo(eventRetention) >= 0) {
            LOG.error("Session file detector max-file-age must stay well below workspace event retention, "
                            + "otherwise files outlive their deduplication rows and are announced twice: "
                            + "max_file_age={} queue_events_retention={}",
                    descriptor.maxFileAge(), eventRetention);
        }
    }
}
