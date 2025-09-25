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
import jakarta.ws.rs.Path;
import pbouda.jeffrey.resources.response.ProjectResponse;
import pbouda.jeffrey.resources.response.WorkspaceEventResponse;
import pbouda.jeffrey.resources.response.WorkspaceResponse;

import java.util.List;

public interface WorkspaceResource {

    @DELETE
    void delete();

    @GET
    WorkspaceResponse info();

    @GET
    @Path("/events")
    List<WorkspaceEventResponse> events();

    @GET
    @Path("/projects")
    List<ProjectResponse> projects();
}
