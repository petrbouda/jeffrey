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

package pbouda.jeffrey.manager;

import pbouda.jeffrey.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.provider.api.repository.WorkspaceRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class WorkspacesManagerImpl implements WorkspacesManager {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceManager.Factory workspaceManagerFactory;

    public WorkspacesManagerImpl(
            WorkspaceRepository workspaceRepository,
            WorkspaceManager.Factory workspaceManagerFactory) {

        this.workspaceRepository = workspaceRepository;
        this.workspaceManagerFactory = workspaceManagerFactory;
    }

    @Override
    public WorkspaceInfo create(String id, String name, String description, String path) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Workspace ID cannot be null or empty");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Workspace name cannot be null or empty");
        }

        String trimmedId = id.trim();
        String trimmedName = name.trim();

        // Check if workspace with same ID already exists
        if (workspaceRepository.findById(trimmedId).isPresent()) {
            throw new IllegalArgumentException("Workspace with ID '" + trimmedId + "' already exists");
        }

        // Check if workspace with same name already exists
        if (workspaceRepository.existsByName(trimmedName)) {
            throw new IllegalArgumentException("Workspace with name '" + trimmedName + "' already exists");
        }

        WorkspaceInfo workspaceInfo = new WorkspaceInfo(
                trimmedId,
                trimmedName,
                description != null && !description.trim().isEmpty() ? description.trim() : null,
                path != null && !path.trim().isEmpty() ? path.trim() : null,
                true, // enabled by default
                Instant.now(),
                0 // no projects initially
        );

        return workspaceRepository.create(workspaceInfo);
    }

    @Override
    public List<? extends WorkspaceManager> allWorkspaces() {
        return workspaceRepository.findAll().stream()
                .map(workspaceManagerFactory)
                .toList();
    }

    @Override
    public Optional<WorkspaceManager> workspace(String workspaceId) {
        if (workspaceId == null || workspaceId.trim().isEmpty()) {
            return Optional.empty();
        }
        return workspaceRepository.findById(workspaceId.trim())
                .map(workspaceManagerFactory);
    }
}
