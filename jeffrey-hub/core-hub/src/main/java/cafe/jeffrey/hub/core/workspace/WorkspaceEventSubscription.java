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

import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventType;

import java.util.Set;

/**
 * Parameters of a live workspace event subscription.
 *
 * @param workspaceId the workspace to stream events for
 * @param fromOffset  exclusive lower bound; {@code 0} starts from the oldest retained event
 * @param eventTypes  types to deliver; an empty set means all types
 * @param sendEmptyBatches whether to emit empty batches as heartbeats while idle
 * @param batchSize   maximum events per delivered batch
 */
public record WorkspaceEventSubscription(
        String workspaceId,
        long fromOffset,
        Set<WorkspaceEventType> eventTypes,
        boolean sendEmptyBatches,
        int batchSize) {

    public WorkspaceEventSubscription {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("workspaceId must not be blank");
        }
        if (fromOffset < 0) {
            throw new IllegalArgumentException("fromOffset must not be negative: " + fromOffset);
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive: " + batchSize);
        }
        eventTypes = eventTypes == null ? Set.of() : Set.copyOf(eventTypes);
    }

    /**
     * Whether the given type should be delivered. An empty filter accepts everything.
     */
    public boolean accepts(WorkspaceEventType eventType) {
        return eventTypes.isEmpty() || eventTypes.contains(eventType);
    }
}
