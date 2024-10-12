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

package pbouda.jeffrey.controller.project;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.media.multipart.BodyPart;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.controller.request.DeleteRecordingRequest;
import pbouda.jeffrey.controller.util.RecordingUtils;
import pbouda.jeffrey.manager.ProjectManager;

import java.io.InputStream;

public class ProjectRecordingsResource {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectRecordingsResource.class);

    private final ProjectManager projectManager;

    public ProjectRecordingsResource(ProjectManager projectManager) {
        this.projectManager = projectManager;
    }

    @GET
    public JsonNode recordings() {
        return RecordingUtils.toUiTree(projectManager.recordings());
    }

    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response uploadMultiple(@FormDataParam("files[]") FormDataBodyPart body) {
        for (BodyPart part : body.getParent().getBodyParts()) {
            String filename = part.getContentDisposition().getFileName();
            try {
                projectManager.uploadRecording(filename, part.getEntityAs(InputStream.class));
            } catch (Exception e) {
                LOG.error("Couldn't load recording: {}", filename, e);
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("Invalid JFR file: " + filename)
                        .build();
            }
        }

        return Response.noContent().build();
    }

    @POST
    @Path("/delete")
    public void deleteRecording(DeleteRecordingRequest request) {
        for (String filePath : request.filePaths()) {
            projectManager.deleteRecording(java.nio.file.Path.of(filePath));
        }
    }
}
