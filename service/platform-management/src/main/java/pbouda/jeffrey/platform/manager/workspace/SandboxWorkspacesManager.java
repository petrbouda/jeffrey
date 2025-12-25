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

package pbouda.jeffrey.platform.manager.workspace;

import pbouda.jeffrey.common.IDGenerator;
import pbouda.jeffrey.common.model.workspace.WorkspaceStatus;
import pbouda.jeffrey.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.common.model.workspace.WorkspaceType;
import pbouda.jeffrey.provider.api.repository.WorkspacesRepository;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

public final class SandboxWorkspacesManager implements WorkspacesManager {

    private final Clock clock;
    private final WorkspacesRepository workspacesRepository;
    private final WorkspaceManager.Factory workspaceManagerFactory;

    public SandboxWorkspacesManager(
            Clock clock,
            WorkspacesRepository workspacesRepository,
            WorkspaceManager.Factory workspaceManagerFactory) {

        this.clock = clock;
        this.workspacesRepository = workspacesRepository;
        this.workspaceManagerFactory = workspaceManagerFactory;
    }

    @Override
    public List<? extends WorkspaceManager> findAll() {
        return this.workspacesRepository.findAll().stream()
                .filter(WorkspaceInfo::isRemote)
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
        WorkspaceInfo workspaceInfo = sandboxWorkspaceInfo(request);
        return workspacesRepository.create(workspaceInfo);
    }

    private WorkspaceInfo sandboxWorkspaceInfo(CreateWorkspaceRequest request) {
        return new WorkspaceInfo(
                null,
                null,
                null,
                request.name(),
                request.description(),
                null,
                null,
                clock.instant(),
                WorkspaceType.SANDBOX,
                WorkspaceStatus.AVAILABLE,
                0);
    }
}
