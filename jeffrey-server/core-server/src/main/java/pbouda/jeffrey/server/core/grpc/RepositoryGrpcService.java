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
import org.springframework.stereotype.Component;
import pbouda.jeffrey.api.v1.*;
import pbouda.jeffrey.server.core.manager.RepositoryManager;
import pbouda.jeffrey.server.core.manager.project.ProjectManager;
import pbouda.jeffrey.server.core.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.server.core.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.shared.common.model.repository.RepositoryStatistics;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventCreator;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
public class RepositoryGrpcService extends RepositoryServiceGrpc.RepositoryServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(RepositoryGrpcService.class);

    private final WorkspacesManager workspacesManager;
    private final Clock clock;

    public RepositoryGrpcService(WorkspacesManager workspacesManager, Clock clock) {
        this.workspacesManager = workspacesManager;
        this.clock = clock;
    }

    @Override
    public void listSessions(ListSessionsRequest request, StreamObserver<ListSessionsResponse> responseObserver) {
        try {
            ProjectManager project = findProject(request.getWorkspaceId(), request.getProjectId());

            List<RecordingSession> sessions = project.repositoryManager()
                    .listRecordingSessions(true).stream()
                    .map(s -> toProto(s, clock))
                    .toList();

            LOG.debug("Listed sessions via gRPC: projectId={} count={}", request.getProjectId(), sessions.size());

            ListSessionsResponse response = ListSessionsResponse.newBuilder()
                    .addAllSessions(sessions)
                    .build();
            responseObserver.onNext(response);
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
            ProjectManager project = findProject(request.getWorkspaceId(), request.getProjectId());

            pbouda.jeffrey.shared.common.model.repository.RecordingSession session =
                    project.repositoryManager().findRecordingSessions(request.getSessionId())
                            .orElseThrow(() -> Status.NOT_FOUND
                                    .withDescription("Session not found: " + request.getSessionId())
                                    .asRuntimeException());

            LOG.debug("Fetched session via gRPC: sessionId={}", request.getSessionId());

            GetSessionResponse response = GetSessionResponse.newBuilder()
                    .setSession(toProto(session, clock))
                    .build();
            responseObserver.onNext(response);
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
            ProjectManager project = findProject(request.getWorkspaceId(), request.getProjectId());
            RepositoryStatistics stats = project.repositoryManager().calculateRepositoryStatistics();

            LOG.debug("Fetched repository statistics via gRPC: projectId={}", request.getProjectId());

            GetRepositoryStatisticsResponse response = GetRepositoryStatisticsResponse.newBuilder()
                    .setTotalSessions(stats.totalSessions())
                    .setSessionStatus(stats.latestSessionStatus() != null ? stats.latestSessionStatus().name() : "")
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
                    .build();
            responseObserver.onNext(response);
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
            ProjectManager project = findProject(request.getWorkspaceId(), request.getProjectId());
            project.repositoryManager().deleteRecordingSession(request.getSessionId(), WorkspaceEventCreator.MANUAL);

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
            ProjectManager project = findProject(request.getWorkspaceId(), request.getProjectId());
            project.repositoryManager().deleteFilesInSession(request.getSessionId(), request.getFileIdsList());

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

    private ProjectManager findProject(String workspaceId, String projectId) {
        WorkspaceManager workspace = workspacesManager.findById(workspaceId)
                .orElseThrow(() -> Status.NOT_FOUND
                        .withDescription("Workspace not found: " + workspaceId)
                        .asRuntimeException());
        return workspace.projectsManager().project(projectId)
                .orElseThrow(() -> Status.NOT_FOUND
                        .withDescription("Project not found: " + projectId)
                        .asRuntimeException());
    }

    static RecordingSession toProto(
            pbouda.jeffrey.shared.common.model.repository.RecordingSession session, Clock clock) {

        Instant end = session.finishedAt() != null ? session.finishedAt() : clock.instant();
        long durationMillis = end.toEpochMilli() - session.createdAt().toEpochMilli();

        RecordingSession.Builder builder = RecordingSession.newBuilder()
                .setId(session.id())
                .setName(session.name() != null ? session.name() : "")
                .setCreatedAt(String.valueOf(session.createdAt().toEpochMilli()))
                .setStatus(session.status().name())
                .setDuration(String.valueOf(durationMillis));

        if (session.instanceId() != null) {
            builder.setInstanceId(session.instanceId());
        }
        if (session.finishedAt() != null) {
            builder.setFinishedAt(String.valueOf(session.finishedAt().toEpochMilli()));
        }
        if (session.profilerSettings() != null) {
            builder.setProfilerSettings(session.profilerSettings());
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
                .setCreatedAt(file.createdAt() != null ? String.valueOf(file.createdAt().toEpochMilli()) : "")
                .setSize(file.size() != null ? file.size() : 0)
                .setFileType(file.fileType() != null ? file.fileType().name() : "")
                .setStatus(file.status() != null ? file.status().name() : "")
                .build();
    }
}
