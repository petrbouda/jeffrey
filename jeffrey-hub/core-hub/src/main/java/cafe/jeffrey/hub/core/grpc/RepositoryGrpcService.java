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

package cafe.jeffrey.hub.core.grpc;

import cafe.jeffrey.hub.core.project.repository.SessionDetail;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.api.v1.*;
import cafe.jeffrey.hub.core.manager.RepositoryManager;
import cafe.jeffrey.hub.model.repository.RecordingSessionFilter;

import java.util.List;

public class RepositoryGrpcService extends RepositoryServiceGrpc.RepositoryServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(RepositoryGrpcService.class);

    private final GrpcLookups lookups;

    public RepositoryGrpcService(GrpcLookups lookups) {
        this.lookups = lookups;
    }

    @Override
    public void listSessions(ListSessionsRequest request, StreamObserver<ListSessionsResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            RepositoryManager repoManager = lookups.repositoryManager(request.getWorkspaceId(), request.getProjectId());
            RecordingSessionFilter filter = ProtoMappers.sessionFilter(request.getFilter());

            // Loaded with their files for the figures each row carries; the files stay on the hub
            List<RecordingSession> sessions = repoManager.listRecordingSessions(SessionDetail.WITH_FILES, filter).stream()
                    .map(ProtoMappers::sessionHeader)
                    .toList();

            LOG.debug("Listed sessions via gRPC: project_id={} filter={} count={}",
                    request.getProjectId(), filter, sessions.size());

            return ListSessionsResponse.newBuilder()
                    .addAllSessions(sessions)
                    .build();
        });
    }

    @Override
    public void getSession(GetSessionRequest request, StreamObserver<GetSessionResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            RepositoryManager repoManager = lookups.repositoryManager(request.getWorkspaceId(), request.getProjectId());

            var session = repoManager.findRecordingSessions(request.getSessionId())
                    .orElseThrow(() -> GrpcExceptions.notFound("Session not found: " + request.getSessionId()));

            LOG.debug("Fetched session via gRPC: session_id={}", request.getSessionId());

            return GetSessionResponse.newBuilder()
                    .setSession(ProtoMappers.session(session))
                    .build();
        });
    }

    @Override
    public void deleteSession(DeleteSessionRequest request, StreamObserver<DeleteSessionResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            RepositoryManager repoManager = lookups.repositoryManager(request.getWorkspaceId(), request.getProjectId());
            if (!repoManager.deleteRecordingSession(request.getSessionId())) {
                throw GrpcExceptions.notFound("Session not found: " + request.getSessionId());
            }

            LOG.debug("Deleted session via gRPC: session_id={}", request.getSessionId());

            return DeleteSessionResponse.getDefaultInstance();
        });
    }

    @Override
    public void setSessionRetained(SetSessionRetainedRequest request, StreamObserver<SetSessionRetainedResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            RepositoryManager repoManager = lookups.repositoryManager(request.getWorkspaceId(), request.getProjectId());
            if (!repoManager.setSessionRetained(request.getSessionId(), request.getRetained())) {
                throw GrpcExceptions.notFound("Session not found: " + request.getSessionId());
            }

            LOG.debug("Updated session retention via gRPC: session_id={} retained={}",
                    request.getSessionId(), request.getRetained());

            return SetSessionRetainedResponse.getDefaultInstance();
        });
    }

}
