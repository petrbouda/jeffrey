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

package pbouda.jeffrey.provider.api.repository;

import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.common.model.workspace.WorkspaceEventConsumer;

import java.util.List;
import java.util.Optional;

public interface WorkspaceRepository {

    /**
     * Delete the workspace.
     *
     * @return true if the workspace was deleted, false if it didn't exist
     */
    boolean delete();

    /**
     * Find all projects in the workspace.
     *
     * @return list of projects in the workspace.
     */
    List<ProjectInfo> findAllProjects();

    // Workspace Events Methods

    /**
     * Insert a new workspace event.
     *
     * @param workspaceEvent the workspace event to insert
     */
    void insertEvent(WorkspaceEvent workspaceEvent);

    /**
     * Insert multiple workspace events in a batch operation.
     *
     * @param workspaceEvents the list of workspace events to create
     */
    void batchInsertEvents(List<WorkspaceEvent> workspaceEvents);

    /**
     * Find all workspace events for the workspace.
     *
     * @return list of workspace events for the workspace
     */
    List<WorkspaceEvent> findEvents();

    /**
     * Find workspace events for the workspace created after a specific timestamp.
     *
     * @param fromOffset the minimum offset (inclusive)
     * @return list of workspace events created after the specified time
     */
    List<WorkspaceEvent> findEventsFromOffset(long fromOffset);

    // Workspace Event Consumer Methods

    /**
     * Create a new workspace event consumer.
     *
     * @param consumerId the consumer ID
     */
    void createEventConsumer(String consumerId);

    /**
     * Update last processed offset for a workspace event consumer.
     *
     * @param consumerId the consumer ID
     * @param lastOffset the last processed event id (offset)
     */
    void updateEventConsumerOffset(String consumerId, long lastOffset);

    /**
     * Find a workspace event consumer by its ID.
     *
     * @param consumerId the consumer name
     * @return the workspace event consumer if it exists, otherwise an empty optional
     */
    Optional<WorkspaceEventConsumer> findEventConsumer(String consumerId);
}
