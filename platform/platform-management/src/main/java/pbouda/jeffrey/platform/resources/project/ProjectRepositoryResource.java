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

package pbouda.jeffrey.platform.resources.project;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.platform.manager.RecordingsDownloadManager;
import pbouda.jeffrey.platform.manager.RepositoryManager;
import pbouda.jeffrey.platform.manager.project.ProjectManager;
import pbouda.jeffrey.platform.resources.request.SelectedRecordingsRequest;
import pbouda.jeffrey.platform.resources.response.RecordingSessionResponse;
import pbouda.jeffrey.platform.resources.response.RepositoryStatisticsResponse;
import pbouda.jeffrey.profile.manager.model.RepositoryStatistics;
import pbouda.jeffrey.profile.manager.model.StreamedRecordingFile;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventCreator;

import java.io.IOException;
import java.util.List;

public class ProjectRepositoryResource {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectRepositoryResource.class);

    private final RepositoryManager repositoryManager;
    private final RecordingsDownloadManager recordingsDownloadManager;

    public ProjectRepositoryResource(ProjectManager projectManager) {
        this.repositoryManager = projectManager.repositoryManager();
        this.recordingsDownloadManager = projectManager.recordingsDownloadManager();
    }

    @GET
    @Path("/sessions")
    public List<RecordingSessionResponse> listRepositorySessions() {
        LOG.debug("Listing repository sessions");
        return repositoryManager.listRecordingSessions(true).stream()
                .map(RecordingSessionResponse::from)
                .toList();
    }

    @GET
    @Path("/statistics")
    public RepositoryStatisticsResponse getRepositoryStatistics() {
        LOG.debug("Fetching repository statistics");
        RepositoryStatistics stats = repositoryManager.calculateRepositoryStatistics();
        return RepositoryStatisticsResponse.from(stats);
    }

    @POST
    @Path("/sessions/download")
    public void downloadSession(SelectedRecordingsRequest request) {
        LOG.debug("Downloading session recordings: sessionId={}", request.sessionId());
        recordingsDownloadManager.mergeAndDownloadSession(request.sessionId());
    }

    @DELETE
    @Path("/sessions/{sessionId}")
    public void deleteSession(@PathParam("sessionId") String sessionId) {
        LOG.debug("Deleting repository session: sessionId={}", sessionId);
        repositoryManager.deleteRecordingSession(sessionId, WorkspaceEventCreator.MANUAL);
    }

    @POST
    @Path("/recordings/download")
    public void downloadSelectedRecordings(SelectedRecordingsRequest request) {
        LOG.debug("Downloading selected recordings: fileCount={}", request.recordingIds() != null ? request.recordingIds().size() : 0);
        recordingsDownloadManager.mergeAndDownloadRecordings(request.sessionId(), request.recordingIds());
    }

    @POST
    @Path("/recordings/delete")
    public void deleteRecording(SelectedRecordingsRequest request) {
        LOG.debug("Deleting recording from repository");
        repositoryManager.deleteFilesInSession(request.sessionId(), request.recordingIds());
    }

    @GET
    @Path("/sessions/{sessionId}/files/{fileId}/download")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response downloadFile(
            @PathParam("sessionId") String sessionId,
            @PathParam("fileId") String fileId) {

        LOG.debug("Downloading session file: sessionId={} fileId={}", sessionId, fileId);
        StreamedRecordingFile file = repositoryManager.streamFile(sessionId, fileId);
        try {
            return Response.ok(file.openStream())
                    .header("Content-Disposition", "attachment; filename=\"" + file.fileName() + "\"")
                    .type(MediaType.APPLICATION_OCTET_STREAM)
                    .build();
        } catch (IOException e) {
            throw new RuntimeException("Failed to open file: " + file.fileName(), e);
        }
    }
}
