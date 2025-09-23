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

import pbouda.jeffrey.common.IDGenerator;
import pbouda.jeffrey.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.common.model.workspace.WorkspaceLocation;
import pbouda.jeffrey.provider.api.repository.WorkspacesRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class WorkspacesManagerImpl implements WorkspacesManager {

    private final WorkspacesRepository workspacesRepository;
    private final WorkspaceManager.Factory workspaceManagerFactory;

    public WorkspacesManagerImpl(
            WorkspacesRepository workspacesRepository,
            WorkspaceManager.Factory workspaceManagerFactory) {

        this.workspacesRepository = workspacesRepository;
        this.workspaceManagerFactory = workspaceManagerFactory;
    }

    @Override
    public WorkspaceInfo create(
            String workspaceSourceId, String name, String description, String location, boolean isMirror) {

        if (workspaceSourceId == null || workspaceSourceId.isBlank()) {
            throw new IllegalArgumentException("Workspace Source ID cannot be null or empty");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Workspace Name cannot be null or empty");
        }

        String trimmedSourceId = workspaceSourceId.trim();
        String trimmedName = name.trim();

        Optional<WorkspaceManager> workspaceManager = workspace(trimmedSourceId);

        // Check if workspace with same ID already exists
        if (workspaceManager.isPresent()) {
            throw new IllegalArgumentException("Workspace with ID '" + trimmedSourceId + "' already exists");
        }

        // Check if workspace with same name already exists
        if (workspacesRepository.existsByName(trimmedName)) {
            throw new IllegalArgumentException("Workspace with name '" + trimmedName + "' already exists");
        }

        WorkspaceInfo workspaceInfo = new WorkspaceInfo(
                IDGenerator.generate(),
                trimmedSourceId,
                trimmedName,
                description != null && !description.trim().isEmpty() ? description.trim() : null,
                WorkspaceLocation.of(location),
                true, // enabled by default
                Instant.now(),
                isMirror,
                0 // no projects initially
        );

        return workspacesRepository.create(workspaceInfo);
    }

    @Override
    public List<? extends WorkspaceManager> findAll(boolean excludeMirrored) {
        return workspacesRepository.findAll().stream()
                .filter(ws -> excludeMirrored(excludeMirrored, ws))
                .map(workspaceManagerFactory)
                .toList();
    }

    private static boolean excludeMirrored(boolean excludeMirrored, WorkspaceInfo ws) {
        return !excludeMirrored || !ws.isMirrored();
    }

    @Override
    public Optional<WorkspaceManager> workspace(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            return Optional.empty();
        }

        return workspacesRepository.find(workspaceId)
                .map(workspaceManagerFactory);
    }

    @Override
    public Optional<WorkspaceManager> workspaceByRepositoryId(String workspaceRepositoryId) {
        if (workspaceRepositoryId == null || workspaceRepositoryId.isBlank()) {
            return Optional.empty();
        }

        return workspacesRepository.findByRepositoryId(workspaceRepositoryId)
                .map(workspaceManagerFactory);
    }
}
