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

package cafe.jeffrey.hub.core.scheduler.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.core.jfr.JfrMessageEmitter;
import cafe.jeffrey.hub.core.manager.project.ProjectsManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.shared.persistentqueue.PersistentQueue;
import cafe.jeffrey.shared.persistentqueue.QueueEntry;
import cafe.jeffrey.hub.core.scheduler.JobContext;
import cafe.jeffrey.hub.core.scheduler.job.descriptor.ProjectsSynchronizerJobDescriptor;
import cafe.jeffrey.hub.core.workspace.WorkspaceEventConsumerType;
import cafe.jeffrey.hub.core.workspace.consumer.WorkspaceEventConsumer;
import cafe.jeffrey.shared.common.model.job.JobType;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventType;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;

public class ProjectsSynchronizerJob extends WorkspaceJob<ProjectsSynchronizerJobDescriptor> {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectsSynchronizerJob.class);

    private static final WorkspaceEventConsumerType CONSUMER = WorkspaceEventConsumerType.PROJECT_SYNCHRONIZER_CONSUMER;

    /**
     * How many times a failing event is retried (one attempt per job tick) before it is
     * dropped as a poison message so it cannot block the queue forever.
     */
    private static final int MAX_PROCESSING_ATTEMPTS = 5;

    private final List<WorkspaceEventConsumer> consumers;
    private final PersistentQueue<WorkspaceEvent> workspaceEventQueue;
    private final TransactionOperations transactionOperations;
    private final Duration period;
    private final EventProcessingAttempts processingAttempts = new EventProcessingAttempts();

    public ProjectsSynchronizerJob(
            List<WorkspaceEventConsumer> consumers,
            PersistentQueue<WorkspaceEvent> workspaceEventQueue,
            TransactionOperations transactionOperations,
            WorkspacesManager workspacesManager,
            Duration period) {

        super(workspacesManager, new ProjectsSynchronizerJobDescriptor());
        this.consumers = consumers;
        this.workspaceEventQueue = workspaceEventQueue;
        this.transactionOperations = transactionOperations;
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

        for (QueueEntry<WorkspaceEvent> entry : sortedEntries) {
            WorkspaceEvent event = fromQueueEntry(entry);
            try {
                // One transaction per event: every consumer write AND the offset acknowledge
                // commit together or roll back together. A failure cannot leave partial state
                // behind (e.g. a created session with a stale instance status), and a processed
                // event can never be replayed after its acknowledge was committed.
                transactionOperations.executeWithoutResult(_ -> {
                    if (shouldSkipEvent(event, projectsManager)) {
                        LOG.debug("Skipping event for unknown or deleted project: event_type={} project_id={}",
                                event.eventType(), event.projectId());
                    } else {
                        for (WorkspaceEventConsumer consumer : consumers) {
                            if (consumer.isApplicable(event)) {
                                consumer.on(event, projectsManager);
                                LOG.debug("Successfully processed: event_type={} event_id={} consumer={}",
                                        event.eventType(), entry.offset(), consumer.getClass().getSimpleName());
                            }
                        }
                    }
                    workspaceEventQueue.acknowledge(workspaceId, CONSUMER.name(), entry.offset());
                });
                processingAttempts.clear(workspaceId);
                context.report().item("workspace=" + workspaceId + " processed: " + event.eventType()
                        + " project=" + event.projectId());
            } catch (Exception e) {
                JfrMessageEmitter.eventProcessingFailed(event.eventType().name(), event.projectId(), e.getMessage());

                int attempts = processingAttempts.recordFailure(workspaceId, entry.offset());
                if (attempts < MAX_PROCESSING_ATTEMPTS) {
                    // The transaction rolled back, so the offset stays before the failed event: it
                    // is retried (together with everything after it) on the next tick. Events are
                    // ordered, so later events must not overtake a failed one.
                    LOG.warn("Failed to process workspace event, will retry: event_type={} event_id={} " +
                                    "project_id={} attempts={} max_attempts={}",
                            event.eventType(), entry.offset(), event.projectId(), attempts, MAX_PROCESSING_ATTEMPTS, e);
                    context.report().failure("workspace=" + workspaceId + " failed (will retry): "
                            + event.eventType() + " project=" + event.projectId()
                            + " attempts=" + attempts + " error=" + e);
                    break;
                }

                // Poison message: drop it after the retry budget is exhausted so it cannot block
                // the queue forever — acknowledge it and continue with the remaining events.
                LOG.error("Dropping workspace event after repeated failures: event_type={} event_id={} " +
                                "project_id={} attempts={}",
                        event.eventType(), entry.offset(), event.projectId(), attempts, e);
                context.report().failure("workspace=" + workspaceId + " dropped poison event: "
                        + event.eventType() + " project=" + event.projectId() + " attempts=" + attempts);
                acknowledgeDroppedEvent(workspaceId, entry.offset());
                processingAttempts.clear(workspaceId);
            }
        }
    }

    private void acknowledgeDroppedEvent(String workspaceId, long offset) {
        try {
            workspaceEventQueue.acknowledge(workspaceId, CONSUMER.name(), offset);
        } catch (Exception e) {
            LOG.error("Failed to acknowledge dropped workspace event: workspace_id={} offset={}",
                    workspaceId, offset, e);
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
                payload.workspaceRefId(),
                payload.eventType(),
                payload.content(),
                payload.originCreatedAt(),
                entry.createdAt(),
                payload.createdBy());
    }
}
