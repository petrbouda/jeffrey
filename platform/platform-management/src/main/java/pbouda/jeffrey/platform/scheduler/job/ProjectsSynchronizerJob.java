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
import pbouda.jeffrey.platform.jfr.JfrMessageEmitter;
import pbouda.jeffrey.platform.manager.SchedulerManager;
import pbouda.jeffrey.platform.manager.project.ProjectsManager;
import pbouda.jeffrey.platform.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.platform.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.platform.workspace.WorkspaceEventConverter;
import pbouda.jeffrey.platform.project.repository.RepositoryStorage;
import pbouda.jeffrey.platform.queue.PersistentQueue;
import pbouda.jeffrey.platform.queue.QueueEntry;
import pbouda.jeffrey.platform.scheduler.JobContext;
import pbouda.jeffrey.platform.scheduler.job.descriptor.JobDescriptorFactory;
import pbouda.jeffrey.platform.scheduler.job.descriptor.ProjectsSynchronizerJobDescriptor;
import pbouda.jeffrey.platform.streaming.HeartbeatReplayReader;
import pbouda.jeffrey.platform.streaming.JfrStreamingConsumerManager;
import pbouda.jeffrey.platform.workspace.WorkspaceEventConsumerType;
import pbouda.jeffrey.platform.workspace.consumer.*;
import pbouda.jeffrey.provider.platform.repository.PlatformRepositories;
import pbouda.jeffrey.shared.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.shared.common.model.job.JobType;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceInfo;

import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;

public class ProjectsSynchronizerJob extends WorkspaceJob<ProjectsSynchronizerJobDescriptor> {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectsSynchronizerJob.class);

    private static final WorkspaceEventConsumerType CONSUMER = WorkspaceEventConsumerType.PROJECT_SYNCHRONIZER_CONSUMER;

    private final PlatformRepositories platformRepositories;
    private final RepositoryStorage.Factory remoteRepositoryStorageFactory;
    private final JfrStreamingConsumerManager streamingConsumerManager;
    private final PersistentQueue<WorkspaceEvent> workspaceEventQueue;
    private final JeffreyDirs jeffreyDirs;
    private final Clock clock;
    private final HeartbeatReplayReader heartbeatReplayReader;
    private final Duration period;

    public ProjectsSynchronizerJob(
            PlatformRepositories platformRepositories,
            RepositoryStorage.Factory remoteRepositoryStorageFactory,
            JfrStreamingConsumerManager streamingConsumerManager,
            PersistentQueue<WorkspaceEvent> workspaceEventQueue,
            WorkspacesManager workspacesManager,
            SchedulerManager schedulerManager,
            JobDescriptorFactory jobDescriptorFactory,
            JeffreyDirs jeffreyDirs,
            Clock clock,
            HeartbeatReplayReader heartbeatReplayReader,
            Duration period) {

        super(workspacesManager, schedulerManager, jobDescriptorFactory);
        this.platformRepositories = platformRepositories;
        this.remoteRepositoryStorageFactory = remoteRepositoryStorageFactory;
        this.streamingConsumerManager = streamingConsumerManager;
        this.workspaceEventQueue = workspaceEventQueue;
        this.jeffreyDirs = jeffreyDirs;
        this.clock = clock;
        this.heartbeatReplayReader = heartbeatReplayReader;
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
                new CreateSessionWorkspaceEventConsumer(projectsManager, platformRepositories, jeffreyDirs, heartbeatReplayReader),
                new StartStreamingWorkspaceEventConsumer(projectsManager, streamingConsumerManager, platformRepositories),
                new StopStreamingWorkspaceEventConsumer(streamingConsumerManager),
                new DeleteSessionWorkspaceEventConsumer(projectsManager, platformRepositories, remoteRepositoryStorageFactory),
                new DeleteProjectWorkspaceEventConsumer(projectsManager, platformRepositories, remoteRepositoryStorageFactory, jeffreyDirs));

        String workspaceId = workspaceInfo.id();
        List<QueueEntry<WorkspaceEvent>> entries = workspaceEventQueue.poll(workspaceId, CONSUMER.name());
        if (entries.isEmpty()) {
            LOG.debug("No workspace events to process for workspace: {}", workspaceId);
            return;
        }

        LOG.info("Processing workspace events for workspace: workspace_id={} size={}",
                workspaceId, entries.size());

        List<QueueEntry<WorkspaceEvent>> sortedEntries = entries.stream()
                .sorted(Comparator.comparingLong(QueueEntry::offset))
                .toList();

        long latestOffset = -1;

        for (QueueEntry<WorkspaceEvent> entry : sortedEntries) {
            WorkspaceEvent event = WorkspaceEventConverter.fromQueueEntry(entry);
            try {
                for (WorkspaceEventConsumer consumer : consumers) {
                    if (consumer.isApplicable(event)) {
                        consumer.on(event, jobDescriptor);
                        LOG.debug("Successfully processed: event_type={} event_id={} consumer={}",
                                event.eventType(), entry.offset(), consumer.getClass().getSimpleName());
                    }
                }
            } catch (Exception e) {
                JfrMessageEmitter.eventProcessingFailed(event.eventType().name(), event.projectId(), e.getMessage());
                LOG.error("Failed to process workspace event, skipping: event_type={} event_id={} project_id={}",
                        event.eventType(), entry.offset(), event.projectId(), e);
            }
            latestOffset = entry.offset();
        }

        // Acknowledge the latest processed offset
        if (latestOffset != -1) {
            try {
                workspaceEventQueue.acknowledge(workspaceId, CONSUMER.name(), latestOffset);

                LOG.info("Updated consumer state for workspace: workspace_id={} consumer={} offset={}",
                        workspaceId, CONSUMER, latestOffset);

                // TODO: Enable queue compaction after testing in production
                // try {
                //     workspaceEventQueue.compact(workspaceId);
                // } catch (Exception e) {
                //     LOG.warn("Failed to compact workspace event queue: workspace_id={}", workspaceId, e);
                // }
            } catch (Exception e) {
                LOG.error("Failed to update consumer state for workspace: {}", workspaceId, e);
            }
        }
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
