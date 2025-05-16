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
import jakarta.ws.rs.core.Response.Status;
import pbouda.jeffrey.common.model.EventSource;
import pbouda.jeffrey.common.model.RepositoryType;
import pbouda.jeffrey.manager.ProjectManager;
import pbouda.jeffrey.manager.RepositoryManager;
import pbouda.jeffrey.model.RepositoryInfo;

public class ProjectRepositoryResource {

    public record CreateRepositoryRequest(
            String repositoryPath,
            RepositoryType repositoryType,
            boolean createIfNotExists,
            String finishedSessionDetectionFile) {
    }

    public record RepositoryResponse(
            boolean directoryExists,
            String repositoryPath,
            String repositoryType,
            String finishedSessionDetectionFile) {
    }

    private final ProjectManager projectManager;
    private final RepositoryManager repositoryManager;

    public ProjectRepositoryResource(ProjectManager projectManager) {
        this.projectManager = projectManager;
        this.repositoryManager = projectManager.repositoryManager();
    }

    @Path("/data")
    public ProjectRepositoryDataResource projectRepositoryDataResource() {
        return new ProjectRepositoryDataResource(projectManager);
    }

    @POST
    public Response createOrReplaceRepository(CreateRepositoryRequest request) {
        RepositoryInfo repositoryInfo = new RepositoryInfo(
                java.nio.file.Path.of(request.repositoryPath()),
                request.repositoryType,
                request.finishedSessionDetectionFile);

        repositoryManager.createOrReplace(request.createIfNotExists(), repositoryInfo);

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
                info.directionExists(),
                info.repositoryPath().toString(),
                mapEventSource(info.repositoryType()).getLabel(),
                info.finishedSessionDetectionFile());
    }

    private static EventSource mapEventSource(RepositoryType repositoryType) {
        return EventSource.valueOf(repositoryType.name());
    }
}
