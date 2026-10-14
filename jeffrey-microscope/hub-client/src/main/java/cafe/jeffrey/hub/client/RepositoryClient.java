/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.hub.client;

import cafe.jeffrey.microscope.grpc.client.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.api.v1.*;
import cafe.jeffrey.hub.client.dto.RecordingSessionResponse;
import cafe.jeffrey.hub.client.dto.RepositoryFileResponse;
import cafe.jeffrey.microscope.model.repository.RecordingSessionFilter;
import cafe.jeffrey.microscope.model.repository.RecordingStatus;

import java.util.List;

public class RepositoryClient {

    private static final Logger LOG = LoggerFactory.getLogger(RepositoryClient.class);

    private final RepositoryServiceGrpc.RepositoryServiceBlockingStub stub;

    public RepositoryClient(GrpcHubConnection connection) {
        this.stub = RepositoryServiceGrpc.newBlockingStub(connection.getChannel());
    }

    public List<RecordingSessionResponse> recordingSessions(ProjectScope scope) {
        return recordingSessions(scope, RecordingSessionFilter.ALL);
    }

    /**
     * Lists the sessions of a project that satisfy the filter, newest first, each with its file
     * count and size but not its files. The filter travels to the hub, so only the matching
     * sessions come back over the wire.
     */
    public List<RecordingSessionResponse> recordingSessions(ProjectScope scope, RecordingSessionFilter filter) {
        return listSessions(ListSessionsRequest.newBuilder()
                .setWorkspaceId(scope.workspaceId())
                .setProjectId(scope.projectId())
                .setFilter(toProto(filter))
                .build());
    }

    private List<RecordingSessionResponse> listSessions(ListSessionsRequest request) {
        ListSessionsResponse response = stub.listSessions(request);
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

    /**
     * One session with its files — the only call that brings files over the wire.
     */
    public RecordingSessionResponse recordingSession(ProjectScope scope, String sessionId) {
        GetSessionResponse response = stub.getSession(
                GetSessionRequest.newBuilder()
                        .setWorkspaceId(scope.workspaceId())
                        .setProjectId(scope.projectId())
                        .setSessionId(sessionId)
                        .build());

        return toSessionResponse(response.getSession());
    }

    public void deleteSession(ProjectScope scope, String sessionId) {
        stub.deleteSession(
                DeleteSessionRequest.newBuilder()
                        .setWorkspaceId(scope.workspaceId())
                        .setProjectId(scope.projectId())
                        .setSessionId(sessionId)
                        .build());

        LOG.debug("Deleted session via gRPC: sessionId={}", sessionId);
    }

    public void setSessionRetained(ProjectScope scope, String sessionId, boolean retained) {
        stub.setSessionRetained(
                SetSessionRetainedRequest.newBuilder()
                        .setWorkspaceId(scope.workspaceId())
                        .setProjectId(scope.projectId())
                        .setSessionId(sessionId)
                        .setRetained(retained)
                        .build());

        LOG.debug("Updated session retention via gRPC: sessionId={} retained={}", sessionId, retained);
    }

    static RecordingSessionResponse toSessionResponse(RecordingSession proto) {
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
                proto.getRetained(),
                proto.getHeartbeatMissing(),
                proto.getFailed(),
                proto.getFileCount(),
                proto.getSizeBytes())
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
                RepositoryFiles.typeOf(proto.getName()),
                RecordingStatus.FINISHED,
                proto.getIsRecording());
    }
}
