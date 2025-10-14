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
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.StreamingOutput;
import pbouda.jeffrey.manager.RepositoryManager;
import pbouda.jeffrey.manager.model.StreamedRecordingFile;
import pbouda.jeffrey.resources.request.FileDownloadRequest;
import pbouda.jeffrey.resources.request.RecordingDownloadRequest;
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

    @POST
    @Path("/recordings")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response streamMergedSessionRecordings(RecordingDownloadRequest request) {
        List<String> recordingFileIds = request.recordingFileIds();

        Optional<StreamedRecordingFile> recordingFileOpt;
        if (recordingFileIds.isEmpty()) {
            recordingFileOpt = repositoryManager.streamRecordingOfMergedSession(request.sessionId());
        } else {
            recordingFileOpt = repositoryManager.streamRecordingFiles(request.sessionId(), recordingFileIds);
        }

        return streamRecording(recordingFileOpt);
    }

    @POST
    @Path("/files")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response streamSingleFile(FileDownloadRequest request) {
        Optional<StreamedRecordingFile> fileOpt = repositoryManager.streamFile(request.sessionId(), request.fileId());
        return streamRecording(fileOpt);
    }

    private static Response streamRecording(Optional<StreamedRecordingFile> recordingFileOpt) {
        if (recordingFileOpt.isEmpty()) {
            return Response.status(Status.NOT_FOUND).build();
        }
        StreamedRecordingFile recordingFile = recordingFileOpt.get();

        StreamingOutput stream = output -> recordingFile.writer().accept(output);
        return Response.ok(stream)
                .header("Content-Disposition", "attachment; filename=\"" + recordingFile.fileName() + "\"")
                .header("Content-Length", recordingFile.size())
                .build();
    }
}
