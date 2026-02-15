/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.platform.manager.workspace.live;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.platform.manager.workspace.WorkspaceEventManager;
import pbouda.jeffrey.platform.queue.PersistentQueue;
import pbouda.jeffrey.platform.queue.QueueEntry;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceInfo;

import java.util.List;

public class LiveWorkspaceEventManager implements WorkspaceEventManager {

    private static final Logger LOG = LoggerFactory.getLogger(LiveWorkspaceEventManager.class);

    private final WorkspaceInfo workspaceInfo;
    private final PersistentQueue<WorkspaceEvent> queue;

    public LiveWorkspaceEventManager(
            WorkspaceInfo workspaceInfo,
            PersistentQueue<WorkspaceEvent> queue) {

        this.workspaceInfo = workspaceInfo;
        this.queue = queue;
    }

    @Override
    public void batchInsertEvents(List<WorkspaceEvent> events) {
        LOG.debug("Batch inserting workspace events: workspace_id={} count={}", workspaceInfo.id(), events.size());
        queue.appendBatch(events);
    }

    @Override
    public List<WorkspaceEvent> findEvents() {
        return queue.findAll().stream()
                .map(LiveWorkspaceEventManager::toWorkspaceEvent)
                .toList();
    }

    @Override
    public PersistentQueue<WorkspaceEvent> queue() {
        return queue;
    }

    static WorkspaceEvent toWorkspaceEvent(QueueEntry<WorkspaceEvent> entry) {
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
