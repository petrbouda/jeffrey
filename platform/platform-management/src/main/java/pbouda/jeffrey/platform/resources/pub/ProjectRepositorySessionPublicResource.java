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

package pbouda.jeffrey.platform.resources.pub;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventCreator;
import pbouda.jeffrey.shared.common.exception.Exceptions;
import pbouda.jeffrey.platform.manager.RepositoryManager;
import pbouda.jeffrey.profile.manager.model.CleanupInputStream;
import pbouda.jeffrey.profile.manager.model.StreamedRecordingFile;
import pbouda.jeffrey.platform.resources.request.FileDownloadRequest;
import pbouda.jeffrey.platform.resources.request.FilesDownloadRequest;
import pbouda.jeffrey.platform.resources.response.RecordingSessionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

public class ProjectRepositorySessionPublicResource {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectRepositorySessionPublicResource.class);

    private final RepositoryManager repositoryManager;

    public ProjectRepositorySessionPublicResource(RepositoryManager repositoryManager) {
        this.repositoryManager = repositoryManager;
    }

    @GET
    public List<RecordingSessionResponse> listSessions() {
        LOG.debug("Listing public repository sessions");
        return repositoryManager.listRecordingSessions(true).stream()
                .map(RecordingSessionResponse::from)
                .toList();
    }

    @GET
    @Path("/{sessionId}")
    public RecordingSessionResponse singleSession(@PathParam("sessionId") String sessionId) {
        LOG.debug("Fetching public repository session: sessionId={}", sessionId);
        Optional<RecordingSessionResponse> sessionOpt = repositoryManager.listRecordingSessions(true).stream()
                .filter(s -> s.id().equals(sessionId))
                .map(RecordingSessionResponse::from)
                .findFirst();

        if (sessionOpt.isEmpty()) {
            throw Exceptions.recordingSessionNotFound(sessionId);
        }

        return sessionOpt.get();
    }

    @POST
    @Path("/{sessionId}/recordings")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response streamAndMergedRecordings(
            @PathParam("sessionId") String sessionId, FilesDownloadRequest request) {

        LOG.debug("Streaming merged recordings: sessionId={}", sessionId);
        StreamedRecordingFile recordingFile = repositoryManager.mergeAndStreamRecordings(sessionId, request.fileIds());
        return streamRecording(recordingFile);
    }

    @POST
    @Path("/{sessionId}/artifact")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response streamSingleFile(
            @PathParam("sessionId") String sessionId, FileDownloadRequest request) {

        LOG.debug("Streaming single file: sessionId={}", sessionId);
        StreamedRecordingFile file = repositoryManager.streamArtifact(sessionId, request.fileId());
        return streamRecording(file);
    }

    @DELETE
    @Path("/{sessionId}")
    public Response deleteSession(@PathParam("sessionId") String sessionId) {
        LOG.debug("Deleting public repository session: sessionId={}", sessionId);
        repositoryManager.deleteRecordingSession(sessionId, WorkspaceEventCreator.MANUAL);
        return Response.noContent().build();
    }

    private static Response streamRecording(StreamedRecordingFile recordingFile) {
        try {
            InputStream stream = Files.newInputStream(recordingFile.path());
            if (recordingFile.cleanup() != null) {
                stream = new CleanupInputStream(stream, recordingFile.cleanup());
            }
            return Response.ok(stream)
                    .header("Content-Disposition", "attachment; filename=\"" + recordingFile.fileName() + "\"")
                    .type(MediaType.APPLICATION_OCTET_STREAM)
                    .build();
        } catch (java.io.IOException e) {
            throw new UncheckedIOException("Failed to open recording file: " + recordingFile.fileName(), e);
        }
    }
}
