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

package cafe.jeffrey.server.core.scheduler.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.server.core.jfr.JfrMessageEmitter;
import cafe.jeffrey.server.core.manager.SchedulerManager;
import cafe.jeffrey.server.core.manager.project.ProjectsManager;
import cafe.jeffrey.server.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.server.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.shared.persistentqueue.PersistentQueue;
import cafe.jeffrey.shared.persistentqueue.QueueEntry;
import cafe.jeffrey.server.core.scheduler.JobContext;
import cafe.jeffrey.server.core.scheduler.job.descriptor.JobDescriptorFactory;
import cafe.jeffrey.server.core.scheduler.job.descriptor.ProjectsSynchronizerJobDescriptor;
import cafe.jeffrey.server.core.workspace.WorkspaceEventConsumerType;
import cafe.jeffrey.server.core.workspace.consumer.WorkspaceEventConsumer;
import cafe.jeffrey.shared.common.model.job.JobType;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventType;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;

public class ProjectsSynchronizerJob extends WorkspaceJob<ProjectsSynchronizerJobDescriptor> {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectsSynchronizerJob.class);

    private static final WorkspaceEventConsumerType CONSUMER = WorkspaceEventConsumerType.PROJECT_SYNCHRONIZER_CONSUMER;

    private final List<WorkspaceEventConsumer> consumers;
    private final PersistentQueue<WorkspaceEvent> workspaceEventQueue;
    private final Duration period;

    public ProjectsSynchronizerJob(
            List<WorkspaceEventConsumer> consumers,
            PersistentQueue<WorkspaceEvent> workspaceEventQueue,
            WorkspacesManager workspacesManager,
            SchedulerManager schedulerManager,
            JobDescriptorFactory jobDescriptorFactory,
            Duration period) {

        super(workspacesManager, schedulerManager, jobDescriptorFactory);
        this.consumers = consumers;
        this.workspaceEventQueue = workspaceEventQueue;
        this.period = period;
    }

    @Override
    protected void executeOnWorkspace(
            WorkspaceManager workspaceManager, ProjectsSynchronizerJobDescriptor jobDescriptor, JobContext context) {
        WorkspaceInfo workspaceInfo = workspaceManager.resolveInfo();
        ProjectsManager projectsManager = workspaceManager.projectsManager();

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
            WorkspaceEvent event = fromQueueEntry(entry);
            try {
                if (shouldSkipEvent(event, projectsManager)) {
                    LOG.debug("Skipping event for unknown or deleted project: event_type={} project_id={}",
                            event.eventType(), event.projectId());
                    latestOffset = entry.offset();
                    continue;
                }

                for (WorkspaceEventConsumer consumer : consumers) {
                    if (consumer.isApplicable(event)) {
                        consumer.on(event, jobDescriptor, projectsManager);
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

    private static boolean shouldSkipEvent(WorkspaceEvent event, ProjectsManager projectsManager) {
        if (event.projectId() == null) {
            return false;
        }

        // Never skip project creation or deletion events
        if (event.eventType() == WorkspaceEventType.PROJECT_CREATED
                || event.eventType() == WorkspaceEventType.PROJECT_DELETED) {
            return false;
        }

        // Skip if project is not found (soft-deleted/unknown)
        return projectsManager.findByOriginProjectId(event.projectId()).isEmpty();
    }

    private static WorkspaceEvent fromQueueEntry(QueueEntry<WorkspaceEvent> entry) {
        WorkspaceEvent payload = entry.payload();
        return new WorkspaceEvent(
                entry.offset(),
                payload.originEventId(),
                payload.projectId(),
                payload.workspaceId(),
                payload.eventType(),
                payload.content(),
                payload.originCreatedAt(),
                entry.createdAt(),
                payload.createdBy());
    }
}
