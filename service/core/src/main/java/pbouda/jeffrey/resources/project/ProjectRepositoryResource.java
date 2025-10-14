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
import pbouda.jeffrey.manager.RecordingsDownloadManager;
import pbouda.jeffrey.manager.RepositoryManager;
import pbouda.jeffrey.manager.model.RepositoryStatistics;
import pbouda.jeffrey.manager.project.ProjectManager;
import pbouda.jeffrey.resources.request.SelectedRecordingsRequest;
import pbouda.jeffrey.resources.response.RecordingSessionResponse;
import pbouda.jeffrey.resources.response.RepositoryStatisticsResponse;

import java.util.List;

public class ProjectRepositoryResource {

    private final RepositoryManager repositoryManager;
    private final RecordingsDownloadManager recordingsDownloadManager;

    public ProjectRepositoryResource(ProjectManager projectManager) {
        this.repositoryManager = projectManager.repositoryManager();
        this.recordingsDownloadManager = projectManager.recordingsDownloadManager();
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
    @Path("/sessions/download")
    public void downloadSession(SelectedRecordingsRequest request) {
        if (request.merge()) {
            recordingsDownloadManager.mergeAndDownloadSession(request.sessionId());
        } else {
            recordingsDownloadManager.downloadSession(request.sessionId());
        }
    }

    @PUT
    @Path("/sessions/delete")
    public void deleteSession(SelectedRecordingsRequest request) {
        repositoryManager.deleteRecordingSession(request.sessionId());
    }

    @POST
    @Path("/recordings/download")
    public void downloadSelectedRecordings(SelectedRecordingsRequest request) {
        if (request.merge()) {
            recordingsDownloadManager.mergeAndDownloadSelectedRawRecordings(request.sessionId(), request.recordingIds());
        } else {
            recordingsDownloadManager.downloadSelectedRawRecordings(request.sessionId(), request.recordingIds());
        }
    }

    @PUT
    @Path("/recordings/delete")
    public void deleteRecording(SelectedRecordingsRequest request) {
        repositoryManager.deleteFilesInSession(request.sessionId(), request.recordingIds());
    }
}
