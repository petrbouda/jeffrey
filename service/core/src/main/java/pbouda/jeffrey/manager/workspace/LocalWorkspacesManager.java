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
import pbouda.jeffrey.common.model.workspace.WorkspaceStatus;
import pbouda.jeffrey.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.common.model.workspace.WorkspaceType;
import pbouda.jeffrey.provider.api.repository.WorkspacesRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class LocalWorkspacesManager implements WorkspacesManager {

    private final WorkspacesRepository workspacesRepository;
    private final WorkspaceManager.Factory workspaceManagerFactory;

    public LocalWorkspacesManager(
            WorkspacesRepository workspacesRepository,
            WorkspaceManager.Factory workspaceManagerFactory) {

        this.workspacesRepository = workspacesRepository;
        this.workspaceManagerFactory = workspaceManagerFactory;
    }

    @Override
    public List<? extends WorkspaceManager> findAll() {
        return workspacesRepository.findAll().stream()
                .filter(WorkspaceInfo::isLocal)
                .map(workspaceManagerFactory)
                .toList();
    }

    @Override
    public Optional<WorkspaceManager> findById(String workspaceId) {
        return workspacesRepository.find(workspaceId)
                .map(workspaceManagerFactory);
    }

    @Override
    public WorkspaceManager mapToWorkspaceManager(WorkspaceInfo info) {
        return workspaceManagerFactory.apply(info);
    }

    @Override
    public WorkspaceInfo create(CreateWorkspaceRequest request) {
        if (request.workspaceSourceId() == null || request.workspaceSourceId().isBlank()) {
            throw new IllegalArgumentException("Workspace Source ID cannot be null or empty");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Workspace Name cannot be null or empty");
        }

        String trimmedSourceId = request.workspaceSourceId().trim();
        String trimmedName = request.name().trim();

        Optional<WorkspaceManager> workspaceManager = findById(trimmedSourceId);

        // Check if workspace with same ID already exists
        if (workspaceManager.isPresent()) {
            throw new IllegalArgumentException("Workspace with ID '" + trimmedSourceId + "' already exists");
        }

        // Check if workspace with same name already exists
        if (workspacesRepository.existsByName(trimmedName)) {
            throw new IllegalArgumentException("Workspace with name '" + trimmedName + "' already exists");
        }

        String description = request.description() != null
                             && !request.description().trim().isEmpty() ? request.description().trim() : null;

        WorkspaceInfo workspaceInfo = new WorkspaceInfo(
                IDGenerator.generate(),
                trimmedSourceId,
                trimmedName,
                description,
                request.location(),
                request.baseLocation(),
                true, // enabled by default
                Instant.now(),
                WorkspaceType.LOCAL,
                WorkspaceStatus.UNKNOWN,
                0 // no projects initially
        );

        return workspacesRepository.create(workspaceInfo);
    }
}
