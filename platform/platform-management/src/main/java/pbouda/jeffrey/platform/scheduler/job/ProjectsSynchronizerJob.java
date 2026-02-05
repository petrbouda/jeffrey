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

package pbouda.jeffrey.platform.scheduler.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.shared.common.model.job.JobType;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventType;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.platform.manager.SchedulerManager;
import pbouda.jeffrey.platform.manager.project.ProjectsManager;
import pbouda.jeffrey.platform.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.platform.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.platform.project.repository.RepositoryStorage;
import pbouda.jeffrey.platform.scheduler.JobContext;
import pbouda.jeffrey.provider.platform.repository.PlatformRepositories;
import pbouda.jeffrey.platform.scheduler.job.descriptor.JobDescriptorFactory;
import pbouda.jeffrey.platform.scheduler.job.descriptor.ProjectsSynchronizerJobDescriptor;
import pbouda.jeffrey.platform.workspace.WorkspaceEventConsumerType;
import pbouda.jeffrey.platform.streaming.JfrStreamingConsumerManager;
import pbouda.jeffrey.platform.workspace.consumer.*;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class ProjectsSynchronizerJob extends WorkspaceJob<ProjectsSynchronizerJobDescriptor> {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectsSynchronizerJob.class);

    private static final WorkspaceEventConsumerType CONSUMER = WorkspaceEventConsumerType.PROJECT_SYNCHRONIZER_CONSUMER;

    private final PlatformRepositories platformRepositories;
    private final RepositoryStorage.Factory remoteRepositoryStorageFactory;
    private final JfrStreamingConsumerManager streamingConsumerManager;
    private final Duration period;

    public ProjectsSynchronizerJob(
            PlatformRepositories platformRepositories,
            RepositoryStorage.Factory remoteRepositoryStorageFactory,
            JfrStreamingConsumerManager streamingConsumerManager,
            WorkspacesManager workspacesManager,
            SchedulerManager schedulerManager,
            JobDescriptorFactory jobDescriptorFactory,
            Duration period) {

        super(workspacesManager, schedulerManager, jobDescriptorFactory);
        this.platformRepositories = platformRepositories;
        this.remoteRepositoryStorageFactory = remoteRepositoryStorageFactory;
        this.streamingConsumerManager = streamingConsumerManager;
        this.period = period;
    }

    @Override
    protected void executeOnWorkspace(
            WorkspaceManager workspaceManager, ProjectsSynchronizerJobDescriptor jobDescriptor, JobContext context) {
        WorkspaceInfo workspaceInfo = workspaceManager.resolveInfo();

        ProjectsManager projectsManager = workspaceManager.projectsManager();
        List<WorkspaceEventConsumer> consumers = List.of(
                new CreateProjectWorkspaceEventConsumer(projectsManager),
                new InstanceCreatedWorkspaceEventConsumer(projectsManager),
                new InstanceFinishedWorkspaceEventConsumer(projectsManager, platformRepositories),
                new CreateSessionWorkspaceEventConsumer(projectsManager, platformRepositories),
                // Not enabled yet
                // new StartStreamingWorkspaceEventConsumer(projectsManager, streamingConsumerManager),
                new StopStreamingWorkspaceEventConsumer(streamingConsumerManager),
                new DeleteSessionWorkspaceEventConsumer(projectsManager, platformRepositories, remoteRepositoryStorageFactory),
                new DeleteProjectWorkspaceEventConsumer(projectsManager, platformRepositories, remoteRepositoryStorageFactory));

        List<WorkspaceEvent> workspaceEvents = workspaceManager.workspaceEventManager().remainingEvents(CONSUMER);
        if (workspaceEvents.isEmpty()) {
            LOG.debug("No workspace events to process for workspace: {}", workspaceInfo.id());
            return;
        }

        LOG.info("Processing workspace events for workspace: workspace_id={} size={}",
                workspaceInfo.id(), workspaceEvents.size());

        List<WorkspaceEvent> sortedEvents = workspaceEvents.stream()
                .sorted(Comparator.comparing(WorkspaceEvent::eventId))
                .toList();

        long latestOffset = -1;

        for (WorkspaceEvent event : sortedEvents) {
            try {
                for (WorkspaceEventConsumer consumer : consumers) {
                    if (consumer.isApplicable(event)) {
                        consumer.on(event, jobDescriptor);
                        LOG.debug("Successfully processed: event_type={} event_id={}", event.eventType(), event.eventId());
                        break;
                    }
                }
            } catch (Exception e) {
                LOG.error("Failed to process workspace event, skipping: event_type={} event_id={} project_id={}",
                        event.eventType(), event.eventId(), event.projectId(), e);
            }
            latestOffset = event.eventId();
        }

        // Update consumer state with the latest processed event timestamp
        if (latestOffset != -1) {
            try {
                workspaceManager.workspaceEventManager().updateConsumer(CONSUMER, latestOffset);

                LOG.info("Updated consumer state for workspace: workspace_id={} consumer={} offset={}",
                        workspaceInfo.id(), CONSUMER, latestOffset);
            } catch (Exception e) {
                LOG.error("Failed to update consumer state for workspace: {}", workspaceInfo.id(), e);
            }
        }
    }

    private static List<WorkspaceEvent> eventsList(
            Map<WorkspaceEventType, List<WorkspaceEvent>> groupedEvents, WorkspaceEventType eventType) {

        return groupedEvents.getOrDefault(eventType, List.of()).stream()
                .sorted(Comparator.comparing(WorkspaceEvent::eventId))
                .toList();
    }

    @Override
    public Duration period() {
        return period;
    }

    @Override
    public JobType jobType() {
        return JobType.PROJECTS_SYNCHRONIZER;
    }
}
