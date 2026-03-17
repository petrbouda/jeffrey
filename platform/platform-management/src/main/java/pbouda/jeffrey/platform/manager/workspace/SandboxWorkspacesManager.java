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

import pbouda.jeffrey.shared.common.model.workspace.WorkspaceStatus;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceType;
import pbouda.jeffrey.provider.platform.repository.WorkspacesRepository;

import java.time.Clock;

public final class SandboxWorkspacesManager extends AbstractTypedWorkspacesManager {

    private final Clock clock;

    public SandboxWorkspacesManager(
            Clock clock,
            WorkspacesRepository workspacesRepository,
            WorkspaceManager.Factory workspaceManagerFactory) {

        super(workspacesRepository, workspaceManagerFactory, WorkspaceType.SANDBOX);
        this.clock = clock;
    }

    @Override
    public WorkspaceInfo create(CreateWorkspaceRequest request) {
        WorkspaceInfo workspaceInfo = new WorkspaceInfo(
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
        return workspacesRepository.create(workspaceInfo);
    }
}
