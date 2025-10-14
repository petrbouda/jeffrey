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

package pbouda.jeffrey.resources.pub;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import pbouda.jeffrey.manager.RepositoryManager;
import pbouda.jeffrey.manager.model.StreamedRecordingFile;
import pbouda.jeffrey.resources.request.FileDownloadRequest;
import pbouda.jeffrey.resources.request.FilesDownloadRequest;
import pbouda.jeffrey.resources.response.RecordingSessionResponse;

import java.util.List;
import java.util.Optional;

public class ProjectRepositorySessionPublicResource {

    private final RepositoryManager repositoryManager;

    public ProjectRepositorySessionPublicResource(RepositoryManager repositoryManager) {
        this.repositoryManager = repositoryManager;
    }

    @GET
    public List<RecordingSessionResponse> listSessions() {
        return repositoryManager.listRecordingSessions(true).stream()
                .map(RecordingSessionResponse::from)
                .toList();
    }

    @GET
    @Path("/{sessionId}")
    public RecordingSessionResponse singleSession(@PathParam("sessionId") String sessionId) {
        Optional<RecordingSessionResponse> sessionOpt = repositoryManager.listRecordingSessions(true).stream()
                .filter(s -> s.id().equals(sessionId))
                .map(RecordingSessionResponse::from)
                .findFirst();

        if (sessionOpt.isEmpty()) {
            throw new NotFoundException("Session not found");
        }

        return sessionOpt.get();
    }

    @POST
    @Path("/{sessionId}/all-recordings")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response streamMergedSessionRecordings(@PathParam("sessionId") String sessionId) {
        Optional<StreamedRecordingFile> recordingFileOpt =
                repositoryManager.streamRecordingOfMergedSession(sessionId);

        return streamRecording(recordingFileOpt);
    }

    @POST
    @Path("/{sessionId}/recordings")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response streamMergedSessionRecordings(
            @PathParam("sessionId") String sessionId, FilesDownloadRequest request) {

        Optional<StreamedRecordingFile> recordingFileOpt =
                repositoryManager.streamRecordingFiles(sessionId, request.fileIds());

        return streamRecording(recordingFileOpt);
    }

    @POST
    @Path("/{sessionId}/files")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response streamSingleFile(
            @PathParam("sessionId") String sessionId, FileDownloadRequest request) {

        Optional<StreamedRecordingFile> fileOpt = repositoryManager.streamFile(sessionId, request.fileId());
        return streamRecording(fileOpt);
    }

    private static Response streamRecording(Optional<StreamedRecordingFile> recordingFileOpt) {
        if (recordingFileOpt.isEmpty()) {
            throw new NotFoundException("Recording file not found");
        }
        StreamedRecordingFile recordingFile = recordingFileOpt.get();

        StreamingOutput stream = output -> recordingFile.writer().accept(output);
        return Response.ok(stream)
                .header("Content-Disposition", "attachment; filename=\"" + recordingFile.fileName() + "\"")
                .header("Content-Length", recordingFile.size())
                .build();
    }
}
