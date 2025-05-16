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
import pbouda.jeffrey.common.model.repository.RecordingSession;
import pbouda.jeffrey.common.model.repository.RecordingStatus;
import pbouda.jeffrey.common.model.repository.RepositoryFile;
import pbouda.jeffrey.common.model.repository.SupportedRecordingFile;
import pbouda.jeffrey.manager.ProjectManager;
import pbouda.jeffrey.manager.RecordingsManager;
import pbouda.jeffrey.manager.RepositoryManager;

import java.time.Instant;
import java.util.List;

public class ProjectRepositoryDataResource {

    public record SingleRequest(String id, boolean merge) {
    }

    public record SelectedRequest(String sessionId, List<String> recordingIds, boolean merge) {
    }

    public record RecordingSessionResponse(
            String id,
            Instant createdAt,
            Instant modifiedAt,
            Instant finishedAt,
            RecordingStatus status,
            List<RepositoryFileResponse> files) {

        public static RecordingSessionResponse from(RecordingSession session) {
            return new RecordingSessionResponse(
                    session.id(),
                    session.createdAt(),
                    session.modifiedAt(),
                    session.finishedAt(),
                    session.status(),
                    session.files().stream()
                            .map(RepositoryFileResponse::from)
                            .toList());
        }
    }

    public record RepositoryFileResponse(
            String id,
            String name,
            Instant createdAt,
            Instant modifiedAt,
            Instant finishedAt,
            Long size,
            SupportedRecordingFile fileType,
            boolean isRecordingFile,
            RecordingStatus status) {

        public static RepositoryFileResponse from(RepositoryFile file) {
            return new RepositoryFileResponse(
                    file.id(),
                    file.name(),
                    file.createdAt(),
                    file.modifiedAt(),
                    file.finishedAt(),
                    file.size(),
                    file.fileType(),
                    file.isRecordingFile(),
                    file.status());
        }
    }

    private final RepositoryManager repositoryManager;
    private final RecordingsManager recordingsManager;

    public ProjectRepositoryDataResource(ProjectManager projectManager) {
        this.repositoryManager = projectManager.repositoryManager();
        this.recordingsManager = projectManager.recordingsManager();
    }

    @GET
    @Path("/sessions")
    public List<RecordingSessionResponse> listRepositorySessions() {
        return repositoryManager.listRecordingSessions().stream()
                .map(RecordingSessionResponse::from)
                .toList();
    }

    @POST
    @Path("/sessions/copy")
    public void copyFromSession(SingleRequest request) {
        if (request.merge) {
            recordingsManager.mergeAndUploadSession(request.id());
        }
    }

    @PUT
    @Path("/sessions/")
    public void deleteSession(SingleRequest request) {
        repositoryManager.deleteRecordingSession(request.id());
    }

    @POST
    @Path("/recordings/copy")
    public void copySelectedRecordings(SelectedRequest request) {
        if (request.merge) {
            recordingsManager.mergeAndUploadSelectedRawRecordings(request.sessionId(), request.recordingIds());
        }
    }

    @PUT
    @Path("/recordings")
    public void deleteRecording(SelectedRequest request) {
        repositoryManager.deleteFilesInSession(request.sessionId(), request.recordingIds());
    }
}
