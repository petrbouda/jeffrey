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

package pbouda.jeffrey.platform.manager.workspace;

import pbouda.jeffrey.provider.platform.repository.WorkspacesRepository;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceType;

import java.util.List;
import java.util.Optional;

/**
 * Base class for workspace managers that operate on a single workspace type.
 * Provides common query methods (findAll, findById, findByOriginId) with
 * type-based filtering. Subclasses only need to implement {@link #create}.
 */
public abstract non-sealed class AbstractTypedWorkspacesManager implements WorkspacesManager {

    protected final WorkspacesRepository workspacesRepository;
    protected final WorkspaceManager.Factory workspaceManagerFactory;
    private final WorkspaceType workspaceType;

    protected AbstractTypedWorkspacesManager(
            WorkspacesRepository workspacesRepository,
            WorkspaceManager.Factory workspaceManagerFactory,
            WorkspaceType workspaceType) {

        this.workspacesRepository = workspacesRepository;
        this.workspaceManagerFactory = workspaceManagerFactory;
        this.workspaceType = workspaceType;
    }

    @Override
    public List<? extends WorkspaceManager> findAll() {
        return workspacesRepository.findAll().stream()
                .filter(info -> info.type() == workspaceType)
                .map(workspaceManagerFactory)
                .toList();
    }

    @Override
    public Optional<WorkspaceManager> findById(String workspaceId) {
        return workspacesRepository.find(workspaceId)
                .filter(info -> info.type() == workspaceType)
                .map(workspaceManagerFactory);
    }

    @Override
    public Optional<WorkspaceManager> findByOriginId(String originId) {
        return workspacesRepository.findByOriginId(originId)
                .filter(info -> info.type() == workspaceType)
                .map(workspaceManagerFactory);
    }

    @Override
    public WorkspaceManager mapToWorkspaceManager(WorkspaceInfo info) {
        return workspaceManagerFactory.apply(info);
    }
}
