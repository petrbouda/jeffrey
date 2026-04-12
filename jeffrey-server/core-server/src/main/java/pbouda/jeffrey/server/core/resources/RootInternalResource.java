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

package pbouda.jeffrey.server.core.resources;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import pbouda.jeffrey.server.core.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.shared.common.JeffreyVersion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Path("/internal")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RootInternalResource {

    private final WorkspacesManager workspacesManager;

    @Inject
    public RootInternalResource(WorkspacesManager workspacesManager) {
        this.workspacesManager = workspacesManager;
    }

    @Path("/workspaces")
    public WorkspacesResource workspacesResource() {
        return new WorkspacesResource(workspacesManager);
    }

    @Path("/grpc-docs")
    public GrpcDocsResource grpcDocsResource() {
        return new GrpcDocsResource();
    }

    @GET
    @Path("/version")
    public Map<String, String> version() {
        return Map.of("version", JeffreyVersion.resolveJeffreyVersion());
    }

    @GET
    @Path("/trigger-oom")
    @Produces(MediaType.TEXT_PLAIN)
    public String triggerOom() {
        List<byte[]> leak = new ArrayList<>();
        while (true) {
            leak.add(new byte[10 * 1024 * 1024]);
        }
    }
}
