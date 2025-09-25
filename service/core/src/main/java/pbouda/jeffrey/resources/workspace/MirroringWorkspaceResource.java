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

package pbouda.jeffrey.resources.workspace;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotSupportedException;
import jakarta.ws.rs.Path;
import pbouda.jeffrey.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.manager.workspace.mirror.MirroringWorkspaceClient;
import pbouda.jeffrey.resources.response.ProjectResponse;
import pbouda.jeffrey.resources.response.WorkspaceEventResponse;
import pbouda.jeffrey.resources.response.WorkspaceResponse;

import java.util.List;

public class MirroringWorkspaceResource implements WorkspaceResource {

    private final WorkspaceManager workspaceManager;
    private final MirroringWorkspaceClient mirroringWorkspaceClient;

    public MirroringWorkspaceResource(
            WorkspaceManager workspaceManager,
            MirroringWorkspaceClient mirroringWorkspaceClient) {
        this.workspaceManager = workspaceManager;
        this.mirroringWorkspaceClient = mirroringWorkspaceClient;
    }

    @Override
    public void delete() {
        workspaceManager.delete();
    }

    @Override
    public WorkspaceResponse info() {
        return WorkspaceMappers.toResponse(workspaceManager.info());
    }

    @Override
    public List<WorkspaceEventResponse> events() {
        throw new NotSupportedException();
    }

    @Override
    public List<ProjectResponse> projects() {
        return mirroringWorkspaceClient.allProjects(workspaceManager.info().repositoryId());
    }
}
