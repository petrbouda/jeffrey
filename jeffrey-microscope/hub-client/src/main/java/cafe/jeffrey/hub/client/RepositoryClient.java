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
import cafe.jeffrey.microscope.model.repository.RecordingSessionFilter;

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
        ListSessionsResponse response = stub.listSessions(ListSessionsRequest.newBuilder()
                .setWorkspaceId(scope.workspaceId())
                .setProjectId(scope.projectId())
                .setFilter(toProto(filter))
                .build());
        return response.getSessionsList().stream()
                .map(ClientProtoMappers::session)
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

        return ClientProtoMappers.session(response.getSession());
    }

    public void deleteSession(ProjectScope scope, String sessionId) {
        stub.deleteSession(
                DeleteSessionRequest.newBuilder()
                        .setWorkspaceId(scope.workspaceId())
                        .setProjectId(scope.projectId())
                        .setSessionId(sessionId)
                        .build());

        LOG.debug("Deleted session via gRPC: workspaceId={} projectId={} sessionId={}",
                scope.workspaceId(), scope.projectId(), sessionId);
    }

    public void setSessionRetained(ProjectScope scope, String sessionId, boolean retained) {
        stub.setSessionRetained(
                SetSessionRetainedRequest.newBuilder()
                        .setWorkspaceId(scope.workspaceId())
                        .setProjectId(scope.projectId())
                        .setSessionId(sessionId)
                        .setRetained(retained)
                        .build());

        LOG.debug("Updated session retention via gRPC: workspaceId={} projectId={} sessionId={} retained={}",
                scope.workspaceId(), scope.projectId(), sessionId, retained);
    }
}
