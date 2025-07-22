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

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import pbouda.jeffrey.appinitializer.JfrEventListenerInitializer;
import pbouda.jeffrey.appinitializer.SchedulerInitializer;
import pbouda.jeffrey.manager.ProjectsManager;
import pbouda.jeffrey.manager.SchedulerManager;
import pbouda.jeffrey.project.repository.RemoteRepositoryStorage;
import pbouda.jeffrey.provider.api.repository.Repositories;
import pbouda.jeffrey.scheduler.task.Job;
import pbouda.jeffrey.scheduler.task.ProjectsSynchronizerJob;
import pbouda.jeffrey.scheduler.task.RecordingGeneratorProjectJob;
import pbouda.jeffrey.scheduler.task.RecordingStorageSynchronizerJob;
import pbouda.jeffrey.scheduler.task.RepositoryCleanerProjectJob;
import pbouda.jeffrey.storage.recording.api.RecordingStorage;

import java.time.Duration;
import java.util.List;

import static pbouda.jeffrey.configuration.AppConfiguration.GLOBAL_SCHEDULER_MANAGER_BEAN;

@Configuration
@Import(ProfileFactoriesConfiguration.class)
public class JobsConfiguration {

    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);

    private final ProjectsManager projectsManager;
    private final RemoteRepositoryStorage.Factory repositoryStorageFactory;
    private final Duration defaultPeriod;

    public JobsConfiguration(
            ProjectsManager projectsManager,
            RemoteRepositoryStorage.Factory repositoryStorageFactory,
            @Value("${jeffrey.job.default.period:}") Duration defaultPeriod) {

        this.defaultPeriod = defaultPeriod == null ? ONE_MINUTE : defaultPeriod;
        this.projectsManager = projectsManager;
        this.repositoryStorageFactory = repositoryStorageFactory;
    }

    @Bean
    @ConditionalOnProperty(name = "jeffrey.job.scheduler.enabled", havingValue = "true", matchIfMissing = true)
    public SchedulerInitializer schedulerInitializer(List<Job> jobs) {
        return new SchedulerInitializer(jobs);
    }

    @Bean
    @ConditionalOnProperty(name = "jeffrey.logging.jfr-events.application", havingValue = "true")
    public JfrEventListenerInitializer jfrEventListenerInitializer() {
        return new JfrEventListenerInitializer();
    }

    @Bean
    public Job repositoryCleanerProjectJob(
            @Value("${jeffrey.job.repository-cleaner.period:}") Duration jobPeriod) {

        return new RepositoryCleanerProjectJob(
                projectsManager,
                repositoryStorageFactory,
                jobPeriod == null ? defaultPeriod : jobPeriod);
    }

    @Bean
    public Job recordingGeneratorProjectJob(
            @Value("${jeffrey.job.recording-generator.period:}") Duration jobPeriod) {

        return new RecordingGeneratorProjectJob(
                projectsManager,
                repositoryStorageFactory,
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

    @Bean
    public Job projectsSynchronizerJob(
            @Qualifier(GLOBAL_SCHEDULER_MANAGER_BEAN) SchedulerManager schedulerManager,
            @Value("${jeffrey.job.projects-synchronizer.period:}") Duration jobPeriod) {

        return new ProjectsSynchronizerJob(
                projectsManager, schedulerManager, jobPeriod == null ? defaultPeriod : jobPeriod);
    }
}
