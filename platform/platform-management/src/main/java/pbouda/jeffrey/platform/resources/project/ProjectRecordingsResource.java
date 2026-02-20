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
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.glassfish.jersey.media.multipart.FormDataParam;
import pbouda.jeffrey.platform.manager.RecordingsManager;
import pbouda.jeffrey.platform.resources.request.CreateFolderRequest;
import pbouda.jeffrey.platform.resources.response.RecordingFileResponse;
import pbouda.jeffrey.platform.resources.response.RecordingsResponse;
import pbouda.jeffrey.provider.platform.model.NewRecording;
import pbouda.jeffrey.provider.platform.model.RecordingFolder;
import pbouda.jeffrey.shared.common.InstantUtils;
import pbouda.jeffrey.shared.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.shared.common.model.RecordingFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.List;

public class ProjectRecordingsResource {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectRecordingsResource.class);

    private final RecordingsManager recordingsManager;

    public ProjectRecordingsResource(RecordingsManager recordingsManager) {
        this.recordingsManager = recordingsManager;
    }

    @GET
    public List<RecordingsResponse> recordings() {
        LOG.debug("Listing recordings");
        return recordingsManager.all().stream()
                .map(rec -> {

                    List<RecordingFileResponse> recordingFiles = rec.files().stream()
                            .map(ProjectRecordingsResource::toRecordingFile)
                            .toList();

                    long sizeInBytesTotal = recordingFiles.stream()
                            .mapToLong(RecordingFileResponse::sizeInBytes)
                            .sum();

                    return new RecordingsResponse(
                            rec.id(),
                            rec.recordingName(),
                            sizeInBytesTotal,
                            rec.recordingDuration().toMillis(),
                            InstantUtils.formatInstant(rec.createdAt()),
                            rec.folderId(),
                            rec.eventSource().name(),
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

        LOG.debug("Uploading recording: filename={} folderId={}", cdh.getFileName(), folderId);
        String trimmedFolderId = folderId == null || folderId.isBlank() ? null : folderId.trim();
        java.nio.file.Path filename = java.nio.file.Path.of(cdh.getFileName());
        String recordingName = FileSystemUtils.filenameWithoutExtension(filename);

        NewRecording recording = new NewRecording(recordingName, cdh.getFileName(), trimmedFolderId);
        recordingsManager.upload(recording, fileInputStream);
        return Response.status(Response.Status.CREATED).build();
    }

    @POST
    @Path("/folders")
    public Response createFolder(CreateFolderRequest request) {
        LOG.debug("Creating recording folder: folderName={}", request.folderName());
        recordingsManager.createFolder(request.folderName());
        return Response.status(Response.Status.CREATED).build();
    }

    @GET
    @Path("/folders")
    public List<RecordingFolder> findAllFolders() {
        LOG.debug("Listing recording folders");
        return recordingsManager.allRecordingFolders();
    }

    @DELETE
    @Path("/folders/{folderId}")
    public void deleteFolder(@PathParam("folderId") String folderId) {
        LOG.debug("Deleting recording folder: folderId={}", folderId);
        recordingsManager.deleteFolder(folderId);
    }

    @DELETE
    @Path("/{recordingId}")
    public void deleteRecording(@PathParam("recordingId") String recordingId) {
        LOG.debug("Deleting recording: recordingId={}", recordingId);
        recordingsManager.delete(recordingId);
    }

    @GET
    @Path("/{recordingId}/files/{fileId}/download")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response downloadFile(
            @PathParam("recordingId") String recordingId,
            @PathParam("fileId") String fileId) {

        LOG.debug("Downloading recording file: recordingId={} fileId={}", recordingId, fileId);
        java.nio.file.Path filePath = recordingsManager.findRecordingFile(recordingId, fileId)
                .orElseThrow(() -> new NotFoundException(
                        "Recording file not found: recordingId=" + recordingId + ", fileId=" + fileId));

        StreamingOutput streamingOutput = (OutputStream output) -> {
            try (InputStream input = Files.newInputStream(filePath)) {
                input.transferTo(output);
            }
        };

        String filename = recordingId + "-" + filePath.getFileName().toString();
        return Response.ok(streamingOutput)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, FileSystemUtils.size(filePath))
                .build();
    }
}
