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

package cafe.jeffrey.hub.core.workspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.persistence.api.WorkspaceEventLogRepository;
import cafe.jeffrey.hub.persistence.api.WorkspaceEventLogRepository.PendingWorkspaceEvent;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventType;

import java.util.List;
import java.util.Set;

/**
 * Publishes workspace events into the append-only audit log ({@code workspace_events} table).
 * Events are announcements, not commands: nothing consumes them — the log only feeds the
 * Activity/Event Log feed and is trimmed by age-based retention.
 *
 * <p>File-announcement events get a dedup key because their producer
 * ({@code SessionFileDetectorProjectJob}) is stateless and re-offers every file on every tick —
 * the unique index collapses those repeats. Every other event type is emitted exactly when its
 * action happens, so it carries no key, and repeats (e.g. a reactivated instance finishing a
 * second time) are legitimately distinct audit rows.</p>
 */
public class AuditWorkspaceEventPublisher implements WorkspaceEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(AuditWorkspaceEventPublisher.class);

    private static final Set<WorkspaceEventType> DEDUPED_EVENT_TYPES = Set.of(
            WorkspaceEventType.PROJECT_INSTANCE_SESSION_RECORDING_FILE_CREATED,
            WorkspaceEventType.PROJECT_INSTANCE_SESSION_ARTIFACT_FILE_CREATED);

    private static final String DEDUP_KEY_SEPARATOR = ":";

    private final WorkspaceEventLogRepository workspaceEventLog;

    public AuditWorkspaceEventPublisher(WorkspaceEventLogRepository workspaceEventLog) {
        this.workspaceEventLog = workspaceEventLog;
    }

    @Override
    public void publish(String workspaceId, WorkspaceEvent event) {
        int inserted = workspaceEventLog.append(workspaceId, event, dedupKey(event));
        LOG.debug("Appended workspace event to audit log: workspace_id={} event_type={} origin_event_id={} inserted={}",
                workspaceId, event.eventType(), event.originEventId(), inserted);
    }

    @Override
    public void publishBatch(String workspaceId, List<WorkspaceEvent> events) {
        if (events.isEmpty()) {
            return;
        }

        List<PendingWorkspaceEvent> pending = events.stream()
                .map(event -> new PendingWorkspaceEvent(event, dedupKey(event)))
                .toList();

        int inserted = workspaceEventLog.appendBatch(workspaceId, pending);
        LOG.debug("Appended workspace event batch to audit log: workspace_id={} offered={} inserted={}",
                workspaceId, events.size(), inserted);
    }

    private static String dedupKey(WorkspaceEvent event) {
        if (!DEDUPED_EVENT_TYPES.contains(event.eventType())) {
            return null;
        }
        return event.projectId() + DEDUP_KEY_SEPARATOR
                + event.originEventId() + DEDUP_KEY_SEPARATOR
                + event.eventType().name();
    }
}
