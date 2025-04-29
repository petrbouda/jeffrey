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

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import pbouda.jeffrey.manager.RepositoryManager;
import pbouda.jeffrey.project.repository.RecordingSession;

import java.util.List;

public class ProjectRepositoryDataResource {

    public record CopyRequest(String id, boolean merge) {
    }

    private final RepositoryManager repositoryManager;

    public ProjectRepositoryDataResource(RepositoryManager repositoryManager) {
        this.repositoryManager = repositoryManager;
    }

    @GET
    @Path("/sessions")
    public List<RecordingSession> listRepositorySessions() {
        return repositoryManager.listRecordingSessions();
    }

    @POST
    @Path("/sessions/download")
    public void downloadFromSession(CopyRequest request) {
        System.out.println();
    }

    @POST
    @Path("/recordings/download")
    public void downloadFromRecording(CopyRequest request) {
        System.out.println();
    }
}
