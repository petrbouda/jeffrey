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

package pbouda.jeffrey.platform.scheduler.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.platform.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.platform.queue.PersistentQueue;
import pbouda.jeffrey.platform.scheduler.Job;
import pbouda.jeffrey.platform.scheduler.JobContext;
import pbouda.jeffrey.platform.scheduler.SchedulerTrigger;
import pbouda.jeffrey.platform.workspace.WorkspaceEventConverter;
import pbouda.jeffrey.shared.common.Json;
import pbouda.jeffrey.shared.common.model.job.JobType;
import pbouda.jeffrey.shared.common.model.workspace.CLIWorkspaceEvent;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.shared.folderqueue.FolderQueue;
import pbouda.jeffrey.shared.folderqueue.FolderQueueEntry;
import pbouda.jeffrey.shared.folderqueue.FolderQueueEntryParser;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Polls the shared {@code workspaces/.events/} folder for event files written by the CLI
 * and replicates them into the persistent DB queue. Events are NOT processed here — they
 * are picked up by {@code ProjectsSynchronizerJob} which owns the complete consumer chain
 * including streaming setup.
 * <p>
 * This is a direct {@link Job} — it reads from a single shared event queue instead of
 * iterating per-workspace directories.
 */
public class WorkspaceEventsReplicatorJob implements Job {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceEventsReplicatorJob.class);

    private final Duration period;
    private final Clock clock;
    private final FolderQueue folderQueue;
    private final WorkspacesManager workspacesManager;
    private final PersistentQueue<WorkspaceEvent> workspaceEventQueue;
    private final SchedulerTrigger migrationCallback;

    private static final FolderQueueEntryParser<CLIWorkspaceEvent> JSON_EVENT_PARSER = (filePath, content) -> {
        try {
            return Optional.of(Json.read(content, CLIWorkspaceEvent.class));
        } catch (Exception e) {
            LOG.debug("Skipping unreadable event file (may be partially written): {}", filePath);
            return Optional.empty();
        }
    };

    public WorkspaceEventsReplicatorJob(
            WorkspacesManager workspacesManager,
            Duration period,
            Clock clock,
            FolderQueue folderQueue,
            PersistentQueue<WorkspaceEvent> workspaceEventQueue,
            SchedulerTrigger migrationCallback) {

        this.workspacesManager = workspacesManager;
        this.period = period;
        this.clock = clock;
        this.folderQueue = folderQueue;
        this.workspaceEventQueue = workspaceEventQueue;
        this.migrationCallback = migrationCallback;
    }

    @Override
    public void execute(JobContext context) {
        try {
            long processed = processEvents();
            if (processed > 0) {
                migrationCallback.execute()
                        .orTimeout(5, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            LOG.error("Failed to process shared workspace events", e);
        }
    }

    private long processEvents() {
        List<FolderQueueEntry<CLIWorkspaceEvent>> entries = folderQueue.poll(JSON_EVENT_PARSER);
        if (entries.isEmpty()) {
            return 0;
        }

        long replicatedCount = 0;
        for (FolderQueueEntry<CLIWorkspaceEvent> entry : entries) {
            WorkspaceEvent event = WorkspaceEventConverter.fromCLIEvent(entry.parsed(), clock.instant());
            try {
                if (workspacesManager.findByOriginId(event.workspaceId()).isEmpty()) {
                    LOG.debug("Workspace not found, skipping event for retry: workspace_id={} event_type={}",
                            event.workspaceId(), event.eventType());
                    continue;
                }

                workspaceEventQueue.append(event.workspaceId(), event);
                folderQueue.acknowledge(entry.filePath());
                replicatedCount++;

                LOG.debug("Replicated folder event to persistent queue: event_type={} file={} workspace_id={}",
                        event.eventType(), entry.filename(), event.workspaceId());
            } catch (Exception e) {
                LOG.error("Failed to replicate folder event, will retry: event_type={} file={} workspace_id={}",
                        event.eventType(), entry.filename(), event.workspaceId(), e);
            }
        }

        if (replicatedCount > 0) {
            LOG.info("Replicated shared workspace events to persistent queue: replicated={} total={}", replicatedCount, entries.size());
        }

        return replicatedCount;
    }

    @Override
    public Duration period() {
        return period;
    }

    @Override
    public JobType jobType() {
        return JobType.WORKSPACE_EVENTS_REPLICATOR;
    }
}
