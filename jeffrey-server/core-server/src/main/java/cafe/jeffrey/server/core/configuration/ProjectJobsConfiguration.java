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

package cafe.jeffrey.server.core.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import cafe.jeffrey.server.core.ServerJeffreyDirs;
import cafe.jeffrey.server.core.configuration.properties.JobProperties;
import cafe.jeffrey.server.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.server.core.project.repository.RepositoryStorage;
import cafe.jeffrey.server.core.project.repository.SessionFinishEventEmitter;
import cafe.jeffrey.server.core.scheduler.PeriodicalScheduler;
import cafe.jeffrey.server.core.scheduler.Scheduler;
import cafe.jeffrey.server.core.scheduler.job.*;
import cafe.jeffrey.server.core.scheduler.job.descriptor.JobDescriptorFactory;
import cafe.jeffrey.server.core.streaming.FileHeartbeatReader;
import cafe.jeffrey.server.core.streaming.SessionFinisher;
import cafe.jeffrey.server.core.workspace.WorkspaceEventPublisher;
import cafe.jeffrey.server.persistence.api.ServerPlatformRepositories;

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
    private final WorkspacesManager workspacesManager;
    private final JobDescriptorFactory jobDescriptorFactory;
    private final JobProperties jobProperties;

    public ProjectJobsConfiguration(
            WorkspacesManager workspacesManager,
            RepositoryStorage.Factory repositoryStorageFactory,
            JobDescriptorFactory jobDescriptorFactory,
            JobProperties jobProperties) {

        this.workspacesManager = workspacesManager;
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
            ServerPlatformRepositories platformRepositories) {
        return new ProjectInstanceSessionCleanerJob(
                workspacesManager,
                repositoryStorageFactory,
                jobDescriptorFactory,
                jobProperties.resolvePeriod("project-instance-session-cleaner"),
                clock,
                platformRepositories);
    }

    @Bean
    public ProjectInstanceRecordingCleanerJob projectInstanceRecordingCleanerJob(Clock clock) {
        return new ProjectInstanceRecordingCleanerJob(
                workspacesManager,
                repositoryStorageFactory,
                jobDescriptorFactory,
                jobProperties.resolvePeriod("project-instance-recording-cleaner"),
                clock);
    }

    @Bean
    public RepositoryCompressionProjectJob repositoryCompressionProjectJob() {
        return new RepositoryCompressionProjectJob(
                workspacesManager,
                repositoryStorageFactory,
                jobDescriptorFactory,
                jobProperties.resolvePeriod("repository-compression"));
    }

    @Bean
    public ExpiredInstanceCleanerJob expiredInstanceCleanerJob(
            Clock clock,
            ServerPlatformRepositories platformRepositories) {
        return new ExpiredInstanceCleanerJob(
                workspacesManager,
                jobDescriptorFactory,
                jobProperties.resolvePeriod("expired-instance-cleaner", Duration.ofHours(1)),
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
            ServerPlatformRepositories platformRepositories) {

        return new SessionFinisher(clock, sessionFinishEventEmitter, fileHeartbeatReader, platformRepositories);
    }

    @Bean
    public SessionFinishedDetectorProjectJob sessionFinishedDetectorProjectJob(
            Clock clock,
            ServerJeffreyDirs jeffreyDirs,
            ServerPlatformRepositories platformRepositories,
            SessionFinisher sessionFinisher,
            @Value("${jeffrey.server.platform.streaming.heartbeat-timeout:10s}") Duration heartbeatTimeout) {

        return new SessionFinishedDetectorProjectJob(
                workspacesManager,
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
