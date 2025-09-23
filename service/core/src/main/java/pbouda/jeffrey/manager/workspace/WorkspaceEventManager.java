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

package pbouda.jeffrey.manager.workspace;

import pbouda.jeffrey.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.workspace.WorkspaceEventConsumerType;

import java.util.List;
import java.util.function.Function;

public interface WorkspaceEventManager {

    @FunctionalInterface
    interface Factory extends Function<WorkspaceInfo, WorkspaceEventManager> {
    }

    /**
     * Batch inserts a list of workspace events into the repository.
     *
     * @param events the list of workspace events to insert
     */
    void batchInsertEvents(List<WorkspaceEvent> events);

    /**
     * Find workspace events that haven't been processed by a consumer for the given type.
     * Returns events of the specified type created after the consumer's last processed event timestamp.
     *
     * @param consumer the workspace event consumer
     * @return list of unprocessed workspace events of the specified type
     */
    List<WorkspaceEvent> remainingEvents(WorkspaceEventConsumerType consumer);

    /**
     * Update the last processed event timestamp for a workspace event consumer.
     *
     * @param consumer   the workspace event consumer type
     * @param lastOffset the offset of the last processed event
     */
    void updateConsumer(WorkspaceEventConsumerType consumer, long lastOffset);

    /**
     * Find all workspace events for this workspace.
     *
     * @return list of all workspace events for this workspace
     */
    List<WorkspaceEvent> findEvents();
}
