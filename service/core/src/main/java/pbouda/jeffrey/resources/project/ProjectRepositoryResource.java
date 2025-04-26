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

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import pbouda.jeffrey.common.model.RepositoryType;
import pbouda.jeffrey.manager.ProfileManager;
import pbouda.jeffrey.manager.RepositoryManager;
import pbouda.jeffrey.model.RepositoryInfo;
import pbouda.jeffrey.resources.project.profile.ProfileResource;


public class ProjectRepositoryResource {

    public record CreateRepositoryRequest(
            String repositoryPath,
            RepositoryType repositoryType,
            boolean createIfNotExists) {
    }

    public record RepositoryResponse(
            String repositoryPath,
            RepositoryType repositoryType,
            boolean directoryExists) {
    }

    private final RepositoryManager repositoryManager;

    public ProjectRepositoryResource(RepositoryManager repositoryManager) {
        this.repositoryManager = repositoryManager;
    }

    @Path("/data")
    public ProjectRepositoryDataResource projectRepositoryDataResource() {
        return new ProjectRepositoryDataResource(repositoryManager);
    }

    @POST
    @Path("/generate")
    public Response generateRecording() {
        repositoryManager.generate();
        return Response.ok().build();
    }

    @POST
    public Response createOrReplaceRepository(CreateRepositoryRequest request) {
        repositoryManager.createOrReplace(
                java.nio.file.Path.of(request.repositoryPath()),
                request.repositoryType,
                request.createIfNotExists());

        return Response.ok().build();
    }

    @GET
    public Response info() {
        return repositoryManager.info()
                .map(ProjectRepositoryResource::toResponse)
                .map(info -> Response.ok(info).build())
                .orElse(Response.status(Status.NOT_FOUND).build());
    }

    @DELETE
    public void delete() {
        repositoryManager.delete();
    }

    private static RepositoryResponse toResponse(RepositoryInfo info) {
        return new RepositoryResponse(
                info.repositoryPath().toString(),
                info.repositoryType(),
                info.directoryExists());
    }
}
