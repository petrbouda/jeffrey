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

package cafe.jeffrey.server.core.workspace;

import cafe.jeffrey.shared.persistentqueue.PersistentQueue;
import cafe.jeffrey.shared.persistentqueue.QueueEntry;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEvent;

import java.util.List;

/**
 * Queue-backed implementation of {@link WorkspaceEventReader} for SERVER mode.
 * Converts queue entries to workspace events internally.
 */
public class QueueWorkspaceEventReader implements WorkspaceEventReader {

    private final PersistentQueue<WorkspaceEvent> workspaceEventQueue;

    public QueueWorkspaceEventReader(PersistentQueue<WorkspaceEvent> workspaceEventQueue) {
        this.workspaceEventQueue = workspaceEventQueue;
    }

    @Override
    public List<WorkspaceEvent> findAll(String workspaceId) {
        return workspaceEventQueue.findAll(workspaceId).stream()
                .map(QueueWorkspaceEventReader::fromQueueEntry)
                .toList();
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
