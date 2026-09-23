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
import cafe.jeffrey.hub.model.repository.RepositoryStatistics;

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
            RepositoryManager repoManager = lookups.repositoryManagerForProject(request.getProjectId());
            RecordingSessionFilter filter = ProtoMappers.sessionFilter(request.getFilter());

            List<RecordingSession> sessions = repoManager.listRecordingSessions(SessionDetail.WITH_FILES, filter).stream()
                    .map(ProtoMappers::session)
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
            RepositoryManager repoManager = lookups.repositoryManagerForSession(request.getSessionId());

            // Resolved twice on purpose: the lookup found the row, this loads the session with its files
            var session = repoManager.findRecordingSessions(request.getSessionId())
                    .orElseThrow(() -> GrpcExceptions.notFound("Session not found: " + request.getSessionId()));

            LOG.debug("Fetched session via gRPC: session_id={}", request.getSessionId());

            return GetSessionResponse.newBuilder()
                    .setSession(ProtoMappers.session(session))
                    .build();
        });
    }

    @Override
    public void getRepositoryStatistics(GetRepositoryStatisticsRequest request, StreamObserver<GetRepositoryStatisticsResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            RepositoryManager repoManager = lookups.repositoryManagerForProject(request.getProjectId());
            RepositoryStatistics stats = repoManager.calculateRepositoryStatistics();

            LOG.debug("Fetched repository statistics via gRPC: project_id={}", request.getProjectId());

            return GetRepositoryStatisticsResponse.newBuilder()
                    .setTotalSize(stats.totalSizeBytes())
                    .build();
        });
    }

    @Override
    public void deleteSession(DeleteSessionRequest request, StreamObserver<DeleteSessionResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            RepositoryManager repoManager = lookups.repositoryManagerForSession(request.getSessionId());
            repoManager.deleteRecordingSession(request.getSessionId());

            LOG.debug("Deleted session via gRPC: session_id={}", request.getSessionId());

            return DeleteSessionResponse.getDefaultInstance();
        });
    }

    @Override
    public void deleteFilesInSession(DeleteFilesInSessionRequest request, StreamObserver<DeleteFilesInSessionResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            RepositoryManager repoManager = lookups.repositoryManagerForSession(request.getSessionId());
            repoManager.deleteFilesInSession(request.getSessionId(), request.getFileIdsList());

            LOG.debug("Deleted files in session via gRPC: session_id={} file_count={}",
                    request.getSessionId(), request.getFileIdsCount());

            return DeleteFilesInSessionResponse.getDefaultInstance();
        });
    }

    @Override
    public void setSessionRetained(SetSessionRetainedRequest request, StreamObserver<SetSessionRetainedResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            RepositoryManager repoManager = lookups.repositoryManagerForSession(request.getSessionId());
            repoManager.setSessionRetained(request.getSessionId(), request.getRetained());

            LOG.debug("Updated session retention via gRPC: session_id={} retained={}",
                    request.getSessionId(), request.getRetained());

            return SetSessionRetainedResponse.getDefaultInstance();
        });
    }

}
