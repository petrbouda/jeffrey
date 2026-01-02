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

package pbouda.jeffrey.platform.resources;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import pbouda.jeffrey.platform.manager.workspace.LiveWorkspacesManager;
import pbouda.jeffrey.platform.resources.pub.WorkspacesPublicResource;
import pbouda.jeffrey.provider.api.repository.ProfilerRepository;

@Path("/public")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RootPublicResource {

    private final LiveWorkspacesManager workspacesManager;
    private final ProfilerRepository profilerRepository;

    @Inject
    public RootPublicResource(
            LiveWorkspacesManager workspacesManager,
            ProfilerRepository profilerRepository) {

        this.workspacesManager = workspacesManager;
        this.profilerRepository = profilerRepository;
    }

    @Path("/workspaces")
    public WorkspacesPublicResource workspaceResource() {
        return new WorkspacesPublicResource(workspacesManager, profilerRepository);
    }
}
