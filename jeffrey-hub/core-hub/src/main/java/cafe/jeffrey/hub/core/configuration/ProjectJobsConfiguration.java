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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import cafe.jeffrey.hub.core.HubJeffreyDirs;
import cafe.jeffrey.hub.core.configuration.properties.SchedulerJobsProperties;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.core.project.repository.SessionFinishEventEmitter;
import cafe.jeffrey.hub.core.scheduler.job.*;
import cafe.jeffrey.hub.core.scheduler.job.descriptor.ExpiredInstanceCleanerJobDescriptor;
import cafe.jeffrey.hub.core.scheduler.job.descriptor.ProjectInstanceRecordingCleanerJobDescriptor;
import cafe.jeffrey.hub.core.scheduler.job.descriptor.ProjectInstanceSessionCleanerJobDescriptor;
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
    public SessionFinisher sessionFinisher(
            Clock clock,
            SessionFinishEventEmitter sessionFinishEventEmitter,
            FileHeartbeatReader fileHeartbeatReader,
            HubPlatformRepositories platformRepositories) {

        return new SessionFinisher(clock, sessionFinishEventEmitter, fileHeartbeatReader, platformRepositories);
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
}
