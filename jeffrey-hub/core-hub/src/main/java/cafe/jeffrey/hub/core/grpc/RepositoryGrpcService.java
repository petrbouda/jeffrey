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

package cafe.jeffrey.hub.core.grpc;

import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.api.v1.*;
import cafe.jeffrey.hub.core.manager.RepositoryManager;
import cafe.jeffrey.shared.common.model.repository.RepositoryStatistics;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventCreator;

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

            List<RecordingSession> sessions = repoManager.listRecordingSessions(true).stream()
                    .map(RepositoryGrpcService::toProto)
                    .toList();

            LOG.debug("Listed sessions via gRPC: projectId={} count={}", request.getProjectId(), sessions.size());

            return ListSessionsResponse.newBuilder()
                    .addAllSessions(sessions)
                    .build();
        });
    }

    @Override
    public void getSession(GetSessionRequest request, StreamObserver<GetSessionResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            RepositoryManager repoManager = lookups.repositoryManagerForSession(request.getSessionId());

            cafe.jeffrey.shared.common.model.repository.RecordingSession session =
                    repoManager.findRecordingSessions(request.getSessionId())
                            .orElseThrow(() -> GrpcExceptions.notFound("Session not found: " + request.getSessionId()));

            LOG.debug("Fetched session via gRPC: sessionId={}", request.getSessionId());

            return GetSessionResponse.newBuilder()
                    .setSession(toProto(session))
                    .build();
        });
    }

    @Override
    public void getRepositoryStatistics(GetRepositoryStatisticsRequest request, StreamObserver<GetRepositoryStatisticsResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            RepositoryManager repoManager = lookups.repositoryManagerForProject(request.getProjectId());
            RepositoryStatistics stats = repoManager.calculateRepositoryStatistics();

            LOG.debug("Fetched repository statistics via gRPC: projectId={}", request.getProjectId());

            return GetRepositoryStatisticsResponse.newBuilder()
                    .setTotalSessions(stats.totalSessions())
                    .setSessionStatus(ProtoMappers.recordingStatus(stats.latestSessionStatus()))
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
        });
    }

    @Override
    public void deleteSession(DeleteSessionRequest request, StreamObserver<DeleteSessionResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            RepositoryManager repoManager = lookups.repositoryManagerForSession(request.getSessionId());
            repoManager.deleteRecordingSession(request.getSessionId(), WorkspaceEventCreator.MANUAL);

            LOG.debug("Deleted session via gRPC: sessionId={}", request.getSessionId());

            return DeleteSessionResponse.getDefaultInstance();
        });
    }

    @Override
    public void deleteFilesInSession(DeleteFilesInSessionRequest request, StreamObserver<DeleteFilesInSessionResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            RepositoryManager repoManager = lookups.repositoryManagerForSession(request.getSessionId());
            repoManager.deleteFilesInSession(request.getSessionId(), request.getFileIdsList());

            LOG.debug("Deleted files in session via gRPC: sessionId={} fileCount={}",
                    request.getSessionId(), request.getFileIdsCount());

            return DeleteFilesInSessionResponse.getDefaultInstance();
        });
    }

    static RecordingSession toProto(
            cafe.jeffrey.shared.common.model.repository.RecordingSession session) {

        RecordingSession.Builder builder = RecordingSession.newBuilder()
                .setId(session.id())
                .setName(ProtoMappers.orEmpty(session.name()))
                .setCreatedAt(session.createdAt().toEpochMilli())
                .setStatus(ProtoMappers.recordingStatus(session.status()));

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

    private static RepositoryFile toFileProto(cafe.jeffrey.shared.common.model.repository.RepositoryFile file) {
        return RepositoryFile.newBuilder()
                .setId(file.id())
                .setName(file.name())
                .setCreatedAt(file.createdAt() != null ? file.createdAt().toEpochMilli() : 0)
                .setSize(file.size() != null ? file.size() : 0)
                .setFileType(file.fileType() != null ? file.fileType().name() : "")
                .setStatus(ProtoMappers.recordingStatus(file.status()))
                .setIsRecording(file.isRecordingFile())
                .build();
    }
}
