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

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.media.multipart.BodyPart;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.RecordingPath;
import pbouda.jeffrey.manager.RecordingsManager;
import pbouda.jeffrey.provider.api.model.NewRecording;
import pbouda.jeffrey.resources.util.RecordingsUtils;

import java.io.InputStream;
import java.util.List;
import java.util.Set;

public class ProjectRecordingsResource {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectRecordingsResource.class);

    public record RecordingsResponse(JsonNode tree, Set<String> suggestions) {
    }

    private final RecordingsManager recordingsManager;

    public ProjectRecordingsResource(RecordingsManager recordingsManager) {
        this.recordingsManager = recordingsManager;
    }

    @GET
    public RecordingsResponse recordings() {
        List<RecordingPath> recordings = recordingsManager.all();
        return new RecordingsResponse(
                RecordingsUtils.toUiTree(recordings),
                RecordingsUtils.toUiSuggestions(recordings));
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response upload(
            @FormDataParam("folder_name") String folderId,
            @FormDataParam("file") InputStream fileInputStream,
            @FormDataParam("file") FormDataContentDisposition cdh) {

        recordingsManager.upload(cdh.getFileName(), folderId, fileInputStream);
        return Response.noContent().build();
    }

    private static java.nio.file.Path resolvePath(String folder, BodyPart part) {
        String filename = part.getContentDisposition().getFileName();
        if (folder == null || folder.isBlank()) {
            return java.nio.file.Path.of(filename);
        } else {
            return java.nio.file.Path.of(folder, filename);
        }
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
