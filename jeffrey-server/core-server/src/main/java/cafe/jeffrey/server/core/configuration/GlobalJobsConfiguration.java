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

package cafe.jeffrey.server.core.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import cafe.jeffrey.server.core.ServerJeffreyDirs;
import cafe.jeffrey.server.core.appinitializer.ApplicationInitializer;
import cafe.jeffrey.server.core.appinitializer.DefaultWorkspaceInitializer;
import cafe.jeffrey.server.core.configuration.properties.DefaultWorkspaceProperties;
import cafe.jeffrey.server.core.configuration.properties.SchedulerJobsProperties;
import cafe.jeffrey.server.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.server.core.scheduler.*;
import cafe.jeffrey.server.core.scheduler.JobRegistry;
import cafe.jeffrey.server.core.scheduler.job.WorkspaceEventsCleanerJob;
import cafe.jeffrey.server.core.scheduler.job.ProjectsSynchronizerJob;
import cafe.jeffrey.server.core.scheduler.job.WorkspaceEventsReplicatorJob;
import cafe.jeffrey.server.core.scheduler.job.ProfilerSettingsSynchronizerJob;
import cafe.jeffrey.server.core.scheduler.job.descriptor.ProfilerSettingsSynchronizerJobDescriptor;
import cafe.jeffrey.server.core.workspace.consumer.WorkspaceEventConsumer;
import cafe.jeffrey.server.persistence.api.ServerPlatformRepositories;
import cafe.jeffrey.shared.common.model.job.JobType;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import cafe.jeffrey.shared.folderqueue.FolderQueue;
import cafe.jeffrey.shared.persistentqueue.PersistentQueue;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static cafe.jeffrey.server.core.configuration.ServerAppConfiguration.GLOBAL_SCHEDULER;
import static cafe.jeffrey.server.core.configuration.ServerAppConfiguration.PROJECTS_SYNCHRONIZER_TRIGGER;

/**
 * Configuration for all background scheduler jobs. Job configuration (enabled
 * flag, period, params) comes from {@link SchedulerJobsProperties} which merges
 * built-in defaults with overrides in {@code application.properties}.
 * <p>
 * The single {@link Scheduler} bean filters all submitted jobs by their
 * enabled flag at construction; disabled jobs are still constructed but never
 * registered for periodic execution.
 */
@Configuration
public class GlobalJobsConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalJobsConfiguration.class);

    private final WorkspacesManager workspacesManager;
    private final SchedulerJobsProperties schedulerJobsProperties;

    public GlobalJobsConfiguration(
            WorkspacesManager workspacesManager,
            SchedulerJobsProperties schedulerJobsProperties) {

        this.workspacesManager = workspacesManager;
        this.schedulerJobsProperties = schedulerJobsProperties;
    }

    @Bean(name = GLOBAL_SCHEDULER, destroyMethod = "close")
    public Scheduler scheduler(List<Job> jobs) {
        List<Job> enabled = jobs.stream()
                .filter(j -> {
                    boolean on = schedulerJobsProperties.forType(j.jobType()).enabled();
                    if (!on) {
                        LOG.info("Scheduler job disabled, skipping registration: job_type={}", j.jobType());
                    }
                    return on;
                })
                .toList();
        LOG.info("Registered scheduler jobs: enabled={} total={}", enabled.size(), jobs.size());
        return new PeriodicalScheduler(enabled);
    }

    @Bean
    public ApplicationInitializer applicationInitializer(ServerPlatformRepositories platformRepositories) {
        return new ApplicationInitializer(platformRepositories.newProfilerRepository());
    }

    @Bean
    public DefaultWorkspaceInitializer defaultWorkspaceInitializer(
            DefaultWorkspaceProperties defaultWorkspaceProperties) {
        return new DefaultWorkspaceInitializer(workspacesManager, defaultWorkspaceProperties);
    }

    @Bean
    public ProjectsSynchronizerJob projectsSynchronizerJob(
            List<WorkspaceEventConsumer> consumers,
            PersistentQueue<WorkspaceEvent> workspaceEventQueue) {

        return new ProjectsSynchronizerJob(
                consumers,
                workspaceEventQueue,
                workspacesManager,
                schedulerJobsProperties.forType(JobType.PROJECTS_SYNCHRONIZER).period());
    }

    @Bean
    public WorkspaceEventsReplicatorJob workspaceEventsReplicatorJob(
            Clock clock,
            ServerJeffreyDirs jeffreyDirs,
            PersistentQueue<WorkspaceEvent> workspaceEventQueue,
            DefaultWorkspaceProperties defaultWorkspaceProperties,
            @Qualifier(PROJECTS_SYNCHRONIZER_TRIGGER) SchedulerTrigger projectsSynchronizerTrigger) {

        return new WorkspaceEventsReplicatorJob(
                workspacesManager,
                schedulerJobsProperties.forType(JobType.WORKSPACE_EVENTS_REPLICATOR).period(),
                clock,
                new FolderQueue(jeffreyDirs.workspaceEvents(), clock),
                workspaceEventQueue,
                projectsSynchronizerTrigger,
                defaultWorkspaceProperties);
    }

    @Bean
    public ProfilerSettingsSynchronizerJob profilerSettingsSynchronizerJob(
            ServerPlatformRepositories platformRepositories) {

        SchedulerJobsProperties.JobConfig config =
                schedulerJobsProperties.forType(JobType.PROFILER_SETTINGS_SYNCHRONIZER);

        int maxVersions = parseInt(config.params().get("max-versions"), 5);

        return new ProfilerSettingsSynchronizerJob(
                config.period(),
                platformRepositories.newProfilerRepository(),
                workspacesManager,
                new ProfilerSettingsSynchronizerJobDescriptor(maxVersions),
                platformRepositories);
    }

    @Bean
    public WorkspaceEventsCleanerJob workspaceEventsCleanerJob(
            PersistentQueue<WorkspaceEvent> workspaceEventQueue,
            Clock clock) {

        SchedulerJobsProperties.JobConfig config = schedulerJobsProperties.forType(JobType.WORKSPACE_EVENTS_CLEANER);
        Duration queueEventsRetention = Duration.parse(
                normalizeDuration(config.params().getOrDefault("queue-events-retention", "P31D")));

        return new WorkspaceEventsCleanerJob(
                workspaceEventQueue,
                clock,
                config.period(),
                queueEventsRetention);
    }

    @Bean
    public JobRegistry jobRegistry() {
        return new JobRegistry(schedulerJobsProperties);
    }

    @Bean(PROJECTS_SYNCHRONIZER_TRIGGER)
    public SchedulerTrigger projectsSynchronizerTrigger(
            @Qualifier(GLOBAL_SCHEDULER) ObjectFactory<Scheduler> scheduler,
            ProjectsSynchronizerJob projectsSynchronizerJob) {
        return new SchedulerTriggerImpl(scheduler, projectsSynchronizerJob);
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Allows users to write {@code 31d} / {@code 5m} / {@code 1h} in property
     * values (Spring's {@code @DurationUnit}-style notation) by translating
     * them to ISO-8601 before {@link Duration#parse} consumes them.
     */
    private static String normalizeDuration(String value) {
        if (value == null || value.isBlank()) {
            return "PT0S";
        }
        String trimmed = value.trim().toLowerCase();
        if (trimmed.startsWith("p") || trimmed.startsWith("-p")) {
            return value.trim().toUpperCase();
        }
        if (trimmed.endsWith("d")) {
            return "P" + trimmed.substring(0, trimmed.length() - 1) + "D";
        }
        if (trimmed.endsWith("h")) {
            return "PT" + trimmed.substring(0, trimmed.length() - 1) + "H";
        }
        if (trimmed.endsWith("m")) {
            return "PT" + trimmed.substring(0, trimmed.length() - 1) + "M";
        }
        if (trimmed.endsWith("s")) {
            return "PT" + trimmed.substring(0, trimmed.length() - 1) + "S";
        }
        return "PT" + trimmed + "S";
    }
}
