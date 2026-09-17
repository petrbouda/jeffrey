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

package cafe.jeffrey.hub.client;

import cafe.jeffrey.microscope.grpc.client.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.api.v1.*;
import cafe.jeffrey.hub.client.dto.RecordingSessionResponse;
import cafe.jeffrey.hub.client.dto.RepositoryFileResponse;
import cafe.jeffrey.hub.client.dto.RepositoryStatisticsResponse;
import cafe.jeffrey.microscope.model.repository.RecordingSessionFilter;
import cafe.jeffrey.microscope.model.repository.RecordingStatus;
import cafe.jeffrey.storage.recording.api.file.ManagedFile;

import java.util.List;

public class RepositoryClient {

    private static final Logger LOG = LoggerFactory.getLogger(RepositoryClient.class);

    private final RepositoryServiceGrpc.RepositoryServiceBlockingStub stub;

    public RepositoryClient(GrpcHubConnection connection) {
        this.stub = RepositoryServiceGrpc.newBlockingStub(connection.getChannel());
    }

    public List<RecordingSessionResponse> recordingSessions(String projectId) {
        return recordingSessions(projectId, RecordingSessionFilter.ALL);
    }

    /**
     * Lists the sessions of a project that satisfy the filter, newest first. The filter travels
     * to the hub, so only the matching sessions come back over the wire.
     */
    public List<RecordingSessionResponse> recordingSessions(String projectId, RecordingSessionFilter filter) {
        ListSessionsResponse response = stub.listSessions(
                ListSessionsRequest.newBuilder()
                        .setProjectId(projectId)
                        .setFilter(toProto(filter))
                        .build());

        return response.getSessionsList().stream()
                .map(RepositoryClient::toSessionResponse)
                .toList();
    }

    private static SessionFilter toProto(RecordingSessionFilter filter) {
        SessionFilter.Builder builder = SessionFilter.newBuilder()
                .setStatus(ClientProtoMappers.recordingStatus(filter.status()))
                .setLimit(filter.limit());
        if (filter.activeFrom() != null) {
            builder.setActiveFrom(filter.activeFrom().toEpochMilli());
        }
        if (filter.activeTo() != null) {
            builder.setActiveTo(filter.activeTo().toEpochMilli());
        }
        return builder.build();
    }

    public RecordingSessionResponse recordingSession(String sessionId) {
        GetSessionResponse response = stub.getSession(
                GetSessionRequest.newBuilder()
                        .setSessionId(sessionId)
                        .build());

        return toSessionResponse(response.getSession());
    }

    public RepositoryStatisticsResponse repositoryStatistics(String projectId) {
        GetRepositoryStatisticsResponse response = stub.getRepositoryStatistics(
                GetRepositoryStatisticsRequest.newBuilder()
                        .setProjectId(projectId)
                        .build());

        return new RepositoryStatisticsResponse(response.getTotalSize());
    }

    public void deleteSession(String sessionId) {
        stub.deleteSession(
                DeleteSessionRequest.newBuilder()
                        .setSessionId(sessionId)
                        .build());

        LOG.debug("Deleted session via gRPC: sessionId={}", sessionId);
    }

    public void deleteFilesInSession(String sessionId, List<String> fileIds) {
        stub.deleteFilesInSession(
                DeleteFilesInSessionRequest.newBuilder()
                        .setSessionId(sessionId)
                        .addAllFileIds(fileIds)
                        .build());

        LOG.debug("Deleted files in session via gRPC: sessionId={} fileCount={}",
                sessionId, fileIds.size());
    }

    public void setSessionRetained(String sessionId, boolean retained) {
        stub.setSessionRetained(
                SetSessionRetainedRequest.newBuilder()
                        .setSessionId(sessionId)
                        .setRetained(retained)
                        .build());

        LOG.debug("Updated session retention via gRPC: sessionId={} retained={}", sessionId, retained);
    }

    private static RecordingSessionResponse toSessionResponse(RecordingSession proto) {
        List<RepositoryFileResponse> files = proto.getFilesList().stream()
                .map(RepositoryClient::toFileResponse)
                .toList();

        return new RecordingSessionResponse(
                proto.getId(),
                ClientProtoMappers.nullIfEmpty(proto.getName()),
                proto.hasInstanceId() ? proto.getInstanceId() : null,
                proto.getCreatedAt(),
                proto.hasFinishedAt() ? proto.getFinishedAt() : null,
                ClientProtoMappers.recordingStatus(proto.getStatus()),
                proto.hasFinishedAt() ? proto.getFinishedAt() - proto.getCreatedAt() : null,
                files,
                proto.getRetained())
                // Here rather than at each call site. A file's status is a fact about its
                // session, so it can only be settled once the whole session is decoded — and
                // settling it inside the one method that decodes one means no caller can be
                // handed a session whose file statuses nobody filled in.
                .withResolvedFileStatuses();
    }

    /**
     * One file as the hub sent it. Its status is left FINISHED here and settled by
     * {@link RecordingSessionResponse#withResolvedFileStatuses()} before the session leaves
     * {@link #toSessionResponse}: the wire carries no status, and a file on its own cannot
     * answer for one.
     *
     * <p>Its type is classified from the name here: the wire carries none, because the hub
     * reads none of the files it serves and has nothing to say about a log or a heap dump
     * beyond that it is not a recording.
     */
    private static RepositoryFileResponse toFileResponse(RepositoryFile proto) {
        return new RepositoryFileResponse(
                proto.getId(),
                proto.getName(),
                proto.getCreatedAt() != 0 ? proto.getCreatedAt() : null,
                proto.getSize(),
                ManagedFile.of(proto.getName()),
                RecordingStatus.FINISHED,
                proto.getIsRecording());
    }
}
