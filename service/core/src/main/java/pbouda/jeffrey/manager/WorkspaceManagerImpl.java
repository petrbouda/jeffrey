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

import pbouda.jeffrey.common.model.Workspace;
import pbouda.jeffrey.provider.api.repository.ProjectsRepository;
import pbouda.jeffrey.provider.api.repository.WorkspaceRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class WorkspaceManagerImpl implements WorkspaceManager {

    private final WorkspaceRepository workspaceRepository;
    private final ProjectsRepository projectsRepository;

    public WorkspaceManagerImpl(WorkspaceRepository workspaceRepository, ProjectsRepository projectsRepository) {
        this.workspaceRepository = workspaceRepository;
        this.projectsRepository = projectsRepository;
    }

    @Override
    public Workspace create(String id, String name, String description, String path) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Workspace ID cannot be null or empty");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Workspace name cannot be null or empty");
        }

        String trimmedId = id.trim();
        String trimmedName = name.trim();
        
        if (trimmedName.isEmpty()) {
            throw new IllegalArgumentException("Workspace name cannot be empty");
        }

        // Check if workspace with same ID already exists
        if (workspaceRepository.findById(trimmedId).isPresent()) {
            throw new IllegalArgumentException("Workspace with ID '" + trimmedId + "' already exists");
        }

        // Check if workspace with same name already exists
        if (workspaceRepository.existsByName(trimmedName)) {
            throw new IllegalArgumentException("Workspace with name '" + trimmedName + "' already exists");
        }

        Workspace workspace = new Workspace(
                trimmedId,
                trimmedName,
                description != null && !description.trim().isEmpty() ? description.trim() : null,
                path != null && !path.trim().isEmpty() ? path.trim() : null,
                true, // enabled by default
                Instant.now(),
                0 // no projects initially
        );

        return workspaceRepository.create(workspace);
    }

    @Override
    public List<Workspace> all() {
        List<Workspace> workspaces = workspaceRepository.findAll();
        return workspaces.stream()
                .map(workspace -> {
                    int projectCount = projectsRepository.findAllProjects(workspace.id()).size();
                    return new Workspace(
                            workspace.id(),
                            workspace.name(),
                            workspace.description(),
                            workspace.path(),
                            workspace.enabled(),
                            workspace.createdAt(),
                            projectCount
                    );
                })
                .toList();
    }

    @Override
    public Optional<Workspace> workspace(String workspaceId) {
        if (workspaceId == null || workspaceId.trim().isEmpty()) {
            return Optional.empty();
        }
        return workspaceRepository.findById(workspaceId.trim());
    }

    @Override
    public boolean delete(String workspaceId) {
        if (workspaceId == null || workspaceId.trim().isEmpty()) {
            return false;
        }
        return workspaceRepository.delete(workspaceId.trim());
    }
}