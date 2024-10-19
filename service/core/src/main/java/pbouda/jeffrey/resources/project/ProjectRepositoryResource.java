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

package pbouda.jeffrey.resources.project;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import pbouda.jeffrey.manager.RepositoryManager;
import pbouda.jeffrey.model.RepositoryInfo;


public class ProjectRepositoryResource {

    public record CreateRepositoryRequest(String repositoryPath, boolean createIfNotExists) {
    }

    private final RepositoryManager repositoryManager;

    public ProjectRepositoryResource(RepositoryManager repositoryManager) {
        this.repositoryManager = repositoryManager;
    }

    @POST
    @Path("/generate")
    public Response generateRecording() {
        repositoryManager.generate();
        return Response.ok().build();
    }

    @POST
    public Response createOrReplaceRepository(CreateRepositoryRequest request) {
        repositoryManager.createOrReplace(java.nio.file.Path.of(request.repositoryPath()), request.createIfNotExists());
        return Response.ok().build();
    }

    @GET
    public Response info() {
        RepositoryInfo info = repositoryManager.info();
        return Response.ok(info).build();
    }

    @DELETE
    public void delete() {
        repositoryManager.delete();
    }
}
