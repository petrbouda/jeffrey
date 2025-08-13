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

import pbouda.jeffrey.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.common.model.workspace.WorkspaceEventConsumer;
import pbouda.jeffrey.common.model.workspace.WorkspaceEventType;
import pbouda.jeffrey.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.common.model.workspace.WorkspaceSessionInfo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WorkspaceRepository {

    /**
     * Find all workspaces.
     *
     * @return list of all workspaces
     */
    List<WorkspaceInfo> findAll();

    /**
     * Find a workspace by its ID.
     *
     * @param workspaceId the workspace ID
     * @return the workspace if it exists, otherwise an empty optional
     */
    Optional<WorkspaceInfo> findById(String workspaceId);

    /**
     * Create a new workspace.
     *
     * @param workspaceInfo the workspace to create
     * @return the created workspace
     */
    WorkspaceInfo create(WorkspaceInfo workspaceInfo);

    /**
     * Delete a workspace by its ID.
     *
     * @param workspaceId the workspace ID
     * @return true if the workspace was deleted, false if it didn't exist
     */
    boolean delete(String workspaceId);

    /**
     * Check if a workspace with the given name already exists.
     *
     * @param name the workspace name
     * @return true if a workspace with this name exists, false otherwise
     */
    boolean existsByName(String name);

    // Workspace Sessions Methods

    /**
     * Create a new workspace session.
     *
     * @param workspaceSessionInfo the workspace session to create
     */
    void createSession(WorkspaceSessionInfo workspaceSessionInfo);

    /**
     * Find all workspace sessions for a given project ID.
     *
     * @param projectId the project ID
     * @return list of workspace sessions for the project
     */
    List<WorkspaceSessionInfo> findSessionsByProjectId(String projectId);

    /**
     * Find a single workspace session by project ID and session ID.
     *
     * @param projectId the project ID
     * @param sessionId the session ID
     * @return the workspace session if it exists, otherwise an empty optional
     */
    Optional<WorkspaceSessionInfo> findSessionByProjectIdAndSessionId(String projectId, String sessionId);

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
     * Find all workspace events for a given workspace ID.
     *
     * @param workspaceId the workspace ID
     * @return list of workspace events for the workspace
     */
    List<WorkspaceEvent> findEventsByWorkspaceId(String workspaceId);

    /**
     * Find workspace events for a given workspace ID and event type.
     *
     * @param workspaceId the workspace ID
     * @param eventType the event type
     * @return list of workspace events matching the criteria
     */
    List<WorkspaceEvent> findEventsByWorkspaceIdAndEventType(String workspaceId, WorkspaceEventType eventType);

    // Workspace Event Consumer Methods

    /**
     * Create a new workspace event consumer.
     *
     * @param workspaceEventConsumer the workspace event consumer to create
     * @return the created workspace event consumer
     */
    WorkspaceEventConsumer createEventConsumer(WorkspaceEventConsumer workspaceEventConsumer);

    /**
     * Update last execution timestamp for a workspace event consumer to the current time.
     *
     * @param consumerName the consumer name
     * @param lastProcessedEventAt the last processed event timestamp
     */
    void updateEventConsumerExecution(String consumerName, Instant lastProcessedEventAt);

    /**
     * Find a workspace event consumer by its name.
     *
     * @param consumerName the consumer name
     * @return the workspace event consumer if it exists, otherwise an empty optional
     */
    Optional<WorkspaceEventConsumer> findEventConsumerByName(String consumerName);
}
