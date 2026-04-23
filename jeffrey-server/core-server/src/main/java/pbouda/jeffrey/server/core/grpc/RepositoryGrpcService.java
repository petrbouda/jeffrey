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

package pbouda.jeffrey.server.core.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.server.api.v1.*;
import pbouda.jeffrey.server.core.manager.RepositoryManager;
import pbouda.jeffrey.server.persistence.model.SessionWithRepository;
import pbouda.jeffrey.server.persistence.repository.ServerPlatformRepositories;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.repository.RepositoryStatistics;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventCreator;

import java.util.List;

public class RepositoryGrpcService extends RepositoryServiceGrpc.RepositoryServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(RepositoryGrpcService.class);

    private final ServerPlatformRepositories platformRepositories;
    private final RepositoryManager.Factory repositoryManagerFactory;

    public RepositoryGrpcService(
            ServerPlatformRepositories platformRepositories,
            RepositoryManager.Factory repositoryManagerFactory) {

        this.platformRepositories = platformRepositories;
        this.repositoryManagerFactory = repositoryManagerFactory;
    }

    @Override
    public void listSessions(ListSessionsRequest request, StreamObserver<ListSessionsResponse> responseObserver) {
        try {
            RepositoryManager repoManager = repositoryManagerForProject(request.getProjectId());

            List<RecordingSession> sessions = repoManager.listRecordingSessions(true).stream()
                    .map(RepositoryGrpcService::toProto)
                    .toList();

            LOG.debug("Listed sessions via gRPC: projectId={} count={}", request.getProjectId(), sessions.size());

            responseObserver.onNext(ListSessionsResponse.newBuilder()
                    .addAllSessions(sessions)
                    .build());
            responseObserver.onCompleted();
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            LOG.error("Failed to list sessions: projectId={}", request.getProjectId(), e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getSession(GetSessionRequest request, StreamObserver<GetSessionResponse> responseObserver) {
        try {
            RepositoryManager repoManager = repositoryManagerForSession(request.getSessionId());

            pbouda.jeffrey.shared.common.model.repository.RecordingSession session =
                    repoManager.findRecordingSessions(request.getSessionId())
                            .orElseThrow(() -> GrpcExceptions.notFound("Session not found: " + request.getSessionId()));

            LOG.debug("Fetched session via gRPC: sessionId={}", request.getSessionId());

            responseObserver.onNext(GetSessionResponse.newBuilder()
                    .setSession(toProto(session))
                    .build());
            responseObserver.onCompleted();
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            LOG.error("Failed to get session: sessionId={}", request.getSessionId(), e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getRepositoryStatistics(GetRepositoryStatisticsRequest request, StreamObserver<GetRepositoryStatisticsResponse> responseObserver) {
        try {
            RepositoryManager repoManager = repositoryManagerForProject(request.getProjectId());
            RepositoryStatistics stats = repoManager.calculateRepositoryStatistics();

            LOG.debug("Fetched repository statistics via gRPC: projectId={}", request.getProjectId());

            responseObserver.onNext(GetRepositoryStatisticsResponse.newBuilder()
                    .setTotalSessions(stats.totalSessions())
                    .setSessionStatus(toProtoRecordingStatus(stats.latestSessionStatus()))
                    .setLastActivityTime(stats.lastActivityTimeMillis())
                    .setTotalSize(stats.totalSizeBytes())
                    .setTotalFiles(stats.totalFiles())
                    .setBiggestSessionSize(stats.biggestSessionSizeBytes())
                    .setJfrFiles(stats.jfr().count())
                    .setJfrSize(stats.jfr().size())
                    .setHeapDumpFiles(stats.heapDump().count())
                    .setHeapDumpSize(stats.heapDump().size())
                    .setLogFiles(stats.log().count())
                    .setLogSize(stats.log().size())
                    .setAppLogFiles(stats.appLog().count())
                    .setAppLogSize(stats.appLog().size())
                    .setErrorLogFiles(stats.errorLog().count())
                    .setErrorLogSize(stats.errorLog().size())
                    .setOtherFiles(stats.other().count())
                    .setOtherSize(stats.other().size())
                    .build());
            responseObserver.onCompleted();
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            LOG.error("Failed to get repository statistics: projectId={}", request.getProjectId(), e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void deleteSession(DeleteSessionRequest request, StreamObserver<DeleteSessionResponse> responseObserver) {
        try {
            RepositoryManager repoManager = repositoryManagerForSession(request.getSessionId());
            repoManager.deleteRecordingSession(request.getSessionId(), WorkspaceEventCreator.MANUAL);

            LOG.debug("Deleted session via gRPC: sessionId={}", request.getSessionId());

            responseObserver.onNext(DeleteSessionResponse.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            LOG.error("Failed to delete session: sessionId={}", request.getSessionId(), e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void deleteFilesInSession(DeleteFilesInSessionRequest request, StreamObserver<DeleteFilesInSessionResponse> responseObserver) {
        try {
            RepositoryManager repoManager = repositoryManagerForSession(request.getSessionId());
            repoManager.deleteFilesInSession(request.getSessionId(), request.getFileIdsList());

            LOG.debug("Deleted files in session via gRPC: sessionId={} fileCount={}",
                    request.getSessionId(), request.getFileIdsCount());

            responseObserver.onNext(DeleteFilesInSessionResponse.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            LOG.error("Failed to delete files in session: sessionId={}", request.getSessionId(), e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    private RepositoryManager repositoryManagerForProject(String projectId) {
        ProjectInfo projectInfo = platformRepositories.newProjectRepository(projectId).find()
                .orElseThrow(() -> GrpcExceptions.notFound("Project not found: " + projectId));
        return repositoryManagerFactory.apply(projectInfo);
    }

    private RepositoryManager repositoryManagerForSession(String sessionId) {
        SessionWithRepository session = platformRepositories.findSessionWithRepositoryById(sessionId)
                .orElseThrow(() -> GrpcExceptions.notFound("Session not found: " + sessionId));
        return repositoryManagerFactory.apply(session.projectInfo());
    }

    static RecordingSession toProto(
            pbouda.jeffrey.shared.common.model.repository.RecordingSession session) {

        RecordingSession.Builder builder = RecordingSession.newBuilder()
                .setId(session.id())
                .setName(session.name() != null ? session.name() : "")
                .setCreatedAt(session.createdAt().toEpochMilli())
                .setStatus(toProtoRecordingStatus(session.status()));

        if (session.instanceId() != null) {
            builder.setInstanceId(session.instanceId());
        }
        if (session.finishedAt() != null) {
            builder.setFinishedAt(session.finishedAt().toEpochMilli());
        }

        if (session.files() != null) {
            session.files().forEach(file -> builder.addFiles(toFileProto(file)));
        }

        return builder.build();
    }

    private static RepositoryFile toFileProto(pbouda.jeffrey.shared.common.model.repository.RepositoryFile file) {
        return RepositoryFile.newBuilder()
                .setId(file.id())
                .setName(file.name())
                .setCreatedAt(file.createdAt() != null ? file.createdAt().toEpochMilli() : 0)
                .setSize(file.size() != null ? file.size() : 0)
                .setFileType(file.fileType() != null ? file.fileType().name() : "")
                .setStatus(toProtoRecordingStatus(file.status()))
                .setIsRecording(file.isRecordingFile())
                .build();
    }

    private static pbouda.jeffrey.server.api.v1.RecordingStatus toProtoRecordingStatus(
            pbouda.jeffrey.shared.common.model.repository.RecordingStatus status) {
        if (status == null) {
            return pbouda.jeffrey.server.api.v1.RecordingStatus.RECORDING_STATUS_UNKNOWN;
        }
        return switch (status) {
            case ACTIVE -> pbouda.jeffrey.server.api.v1.RecordingStatus.RECORDING_STATUS_ACTIVE;
            case FINISHED -> pbouda.jeffrey.server.api.v1.RecordingStatus.RECORDING_STATUS_FINISHED;
            case UNKNOWN -> pbouda.jeffrey.server.api.v1.RecordingStatus.RECORDING_STATUS_UNKNOWN;
        };
    }

}
