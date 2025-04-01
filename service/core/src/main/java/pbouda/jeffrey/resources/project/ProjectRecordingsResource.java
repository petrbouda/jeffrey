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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.manager.RecordingsManager;
import pbouda.jeffrey.provider.api.model.recording.RecordingFolder;
import pbouda.jeffrey.resources.util.Formatter;

import java.io.InputStream;
import java.util.List;

public class ProjectRecordingsResource {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectRecordingsResource.class);

    public record RecordingsResponse(
            String id,
            String name,
            long sizeInBytes,
            long durationInMillis,
            String createdAt,
            boolean hasProfile,
            RecordingFolder folder) {
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
                    return new RecordingsResponse(
                            rec.recording().id(),
                            rec.recording().recordingName(),
                            rec.recording().sizeInBytes(),
                            rec.recording().recordingDuration().toMillis(),
                            Formatter.formatInstant(rec.recording().uploadedAt()),
                            rec.recording().hasProfile(),
                            rec.folder());
                })
                .toList();
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response upload(
            @FormDataParam("folder_name") String folderId,
            @FormDataParam("file") InputStream fileInputStream,
            @FormDataParam("file") FormDataContentDisposition cdh) {

        String trimmedFolderId = folderId == null || folderId.isBlank() ? null : folderId.trim();
        recordingsManager.upload(cdh.getFileName(), trimmedFolderId, fileInputStream);
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
    @Path("/{recordingId}")
    public void deleteRecording(@PathParam("recordingId") String recordingId) {
//        recordingId
//        for (String filePath : request.filePaths()) {
//            recordingsManager.delete(java.nio.file.Path.of(filePath));
//        }
    }
}
