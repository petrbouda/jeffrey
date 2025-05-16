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
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import pbouda.jeffrey.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.common.model.RecordingFile;
import pbouda.jeffrey.manager.RecordingsManager;
import pbouda.jeffrey.provider.api.model.recording.NewRecording;
import pbouda.jeffrey.resources.util.Formatter;

import java.io.InputStream;
import java.util.List;

public class ProjectRecordingsResource {

    public record RecordingsResponse(
            String id,
            String name,
            long sizeInBytes,
            long durationInMillis,
            String uploadedAt,
            String folderId,
            String sourceType,
            boolean hasProfile,
            List<RecordingFileResponse> recordingFiles) {
    }

    public record RecordingFileResponse(String id, String filename, long sizeInBytes, String type, String description) {
    }


    public record CreateFolder(String folderName) {
    }

    private final RecordingsManager recordingsManager;

    public ProjectRecordingsResource(RecordingsManager recordingsManager) {
        this.recordingsManager = recordingsManager;
    }

    @GET
    public List<RecordingsResponse> recordings() {
        return recordingsManager.all().stream()
                .map(rec -> {

                    List<RecordingFileResponse> recordingFiles = rec.files().stream()
                            .map(ProjectRecordingsResource::toRecordingFile)
                            .toList();

                    long sizeInBytesTotal = recordingFiles.stream()
                            .mapToLong(file -> file.sizeInBytes)
                            .sum();

                    return new RecordingsResponse(
                            rec.id(),
                            rec.recordingName(),
                            sizeInBytesTotal,
                            rec.recordingDuration().toMillis(),
                            Formatter.formatInstant(rec.createdAt()),
                            rec.folderId(),
                            rec.eventSource().getLabel(),
                            rec.hasProfile(),
                            recordingFiles);

                })
                .toList();
    }

    private static RecordingFileResponse toRecordingFile(RecordingFile recordingFile) {
        return new RecordingFileResponse(
                recordingFile.id(),
                recordingFile.filename(),
                recordingFile.sizeInBytes(),
                recordingFile.recordingFileType().name(),
                recordingFile.recordingFileType().description());
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response upload(
            @FormDataParam("folder_id") String folderId,
            @FormDataParam("file") InputStream fileInputStream,
            @FormDataParam("file") FormDataContentDisposition cdh) {

        String trimmedFolderId = folderId == null || folderId.isBlank() ? null : folderId.trim();
        java.nio.file.Path filename = java.nio.file.Path.of(cdh.getFileName());
        String recordingName = FileSystemUtils.filenameWithoutExtension(filename);

        NewRecording recording = new NewRecording(recordingName, cdh.getFileName(), trimmedFolderId);
        recordingsManager.upload(recording, fileInputStream);
        return Response.noContent().build();
    }

    @POST
    @Path("/folders")
    public Response create(CreateFolder request) {
        recordingsManager.createFolder(request.folderName());
        return Response.noContent().build();
    }

    @GET
    @Path("/folders")
    public Response findAllFolders() {
        return Response.ok(recordingsManager.allRecordingFolders()).build();
    }

    @DELETE
    @Path("/folders/{folderId}")
    public Response create(@PathParam("folderId") String folderId) {
        recordingsManager.deleteFolder(folderId);
        return Response.ok().build();
    }

    @DELETE
    @Path("/{recordingId}")
    public void deleteRecording(@PathParam("recordingId") String recordingId) {
        recordingsManager.delete(recordingId);
    }
}
