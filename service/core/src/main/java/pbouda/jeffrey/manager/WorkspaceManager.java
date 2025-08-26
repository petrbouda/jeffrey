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

package pbouda.jeffrey.manager;

import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.common.model.workspace.WorkspaceSessionInfo;
import pbouda.jeffrey.repository.RemoteWorkspaceRepository;
import pbouda.jeffrey.workspace.WorkspaceEventConsumerType;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public interface WorkspaceManager {

    @FunctionalInterface
    interface Factory extends Function<WorkspaceInfo, WorkspaceManager> {
    }

    /**
     * Returns the workspace information associated with this manager.
     *
     * @return the workspace information
     */
    WorkspaceInfo info();

    /**
     * Find all projects in the workspace.
     *
     * @return list of projects in the workspace.
     */
    List<ProjectInfo> findAllProjects();

    /**
     * Returns the path to the workspace directory if it exists and is valid.
     *
     * @return an Optional containing the workspace path if valid, empty otherwise
     */
    Optional<Path> workspacePath();

    /**
     * Creates and returns a remote workspace repository instance for this workspace.
     *
     * @return the remote workspace repository
     */
    RemoteWorkspaceRepository remoteWorkspaceRepository();

    /**
     * Batch inserts a list of workspace events into the repository.
     *
     * @param events the list of workspace events to insert
     */
    void batchInsertEvents(List<WorkspaceEvent> events);

    /**
     * Deletes the workspace from the repository.
     */
    void delete();

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
     * Create a new workspace session.
     *
     * @param workspaceSessionInfo the workspace session to create
     */
    void createSession(WorkspaceSessionInfo workspaceSessionInfo);

    /**
     * Find all workspace events for this workspace.
     *
     * @return list of all workspace events for this workspace
     */
    List<WorkspaceEvent> findEvents();
}
