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
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import pbouda.jeffrey.manager.RecordingsManager;
import pbouda.jeffrey.manager.RepositoryManager;
import pbouda.jeffrey.manager.model.RepositoryStatistics;
import pbouda.jeffrey.manager.project.ProjectManager;
import pbouda.jeffrey.resources.response.RecordingSessionResponse;
import pbouda.jeffrey.resources.response.RepositoryStatisticsResponse;

import java.util.List;

public class ProjectRepositoryResource {

    public record SingleRequest(String id, boolean merge) {
    }

    public record SelectedRequest(String sessionId, List<String> recordingIds, boolean merge) {
    }

    private final RepositoryManager repositoryManager;
    private final RecordingsManager recordingsManager;

    public ProjectRepositoryResource(ProjectManager projectManager) {
        this.repositoryManager = projectManager.repositoryManager();
        this.recordingsManager = projectManager.recordingsManager();
    }

    @GET
    @Path("/sessions")
    public List<RecordingSessionResponse> listRepositorySessions() {
        return repositoryManager.listRecordingSessions(true).stream()
                .map(RecordingSessionResponse::from)
                .toList();
    }

    @GET
    @Path("/statistics")
    public RepositoryStatisticsResponse getRepositoryStatistics() {
        RepositoryStatistics stats = repositoryManager.calculateRepositoryStatistics();
        return RepositoryStatisticsResponse.from(stats);
    }

    @POST
    @Path("/sessions/copy")
    public void copyFromSession(SingleRequest request) {
        if (request.merge) {
            recordingsManager.mergeAndUploadSession(request.id());
        } else {
            recordingsManager.uploadSession(request.id());
        }
    }

    @PUT
    @Path("/sessions/delete")
    public void deleteSession(SingleRequest request) {
        repositoryManager.deleteRecordingSession(request.id());
    }

    @POST
    @Path("/recordings/copy")
    public void copySelectedRecordings(SelectedRequest request) {
        if (request.merge) {
            recordingsManager.mergeAndUploadSelectedRawRecordings(request.sessionId(), request.recordingIds());
        } else {
            recordingsManager.uploadSelectedRawRecordings(request.sessionId(), request.recordingIds());
        }
    }

    @PUT
    @Path("/recordings/delete")
    public void deleteRecording(SelectedRequest request) {
        repositoryManager.deleteFilesInSession(request.sessionId(), request.recordingIds());
    }
}
