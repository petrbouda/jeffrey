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

package cafe.jeffrey.hub.stub.grpc;

import cafe.jeffrey.hub.api.v1.GetRepositoryStatisticsRequest;
import cafe.jeffrey.hub.api.v1.GetRepositoryStatisticsResponse;
import cafe.jeffrey.hub.api.v1.GetSessionRequest;
import cafe.jeffrey.hub.api.v1.GetSessionResponse;
import cafe.jeffrey.hub.api.v1.ListSessionsRequest;
import cafe.jeffrey.hub.api.v1.ListSessionsResponse;
import cafe.jeffrey.hub.api.v1.RecordingStatus;
import cafe.jeffrey.hub.api.v1.RepositoryServiceGrpc;
import cafe.jeffrey.hub.stub.data.StubDataset;
import io.grpc.stub.StreamObserver;

import java.time.Instant;
import java.util.List;

/**
 * Stub {@code RepositoryService} backed by the in-memory dataset. Recording sessions
 * are flattened from the project's instances; statistics are aggregated from the
 * in-memory files. Deletion RPCs fall through to {@code UNIMPLEMENTED}.
 */
public class StubRepositoryService extends RepositoryServiceGrpc.RepositoryServiceImplBase {

    private final StubDataset dataset;

    public StubRepositoryService(StubDataset dataset) {
        this.dataset = dataset;
    }

    @Override
    public void listSessions(ListSessionsRequest request, StreamObserver<ListSessionsResponse> responseObserver) {
        ListSessionsResponse.Builder builder = ListSessionsResponse.newBuilder();
        for (StubDataset.Session session : dataset.sessionsForProject(request.getProjectId())) {
            builder.addSessions(StubProtoMappers.recordingSession(session));
        }
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void getSession(GetSessionRequest request, StreamObserver<GetSessionResponse> responseObserver) {
        dataset.session(request.getSessionId())
                .ifPresentOrElse(
                        session -> {
                            responseObserver.onNext(GetSessionResponse.newBuilder()
                                    .setSession(StubProtoMappers.recordingSession(session))
                                    .build());
                            responseObserver.onCompleted();
                        },
                        () -> responseObserver.onError(
                                StubGrpcExceptions.notFound("Session not found: " + request.getSessionId())));
    }

    @Override
    public void getRepositoryStatistics(
            GetRepositoryStatisticsRequest request,
            StreamObserver<GetRepositoryStatisticsResponse> responseObserver) {

        List<StubDataset.Session> sessions = dataset.sessionsForProject(request.getProjectId());
        responseObserver.onNext(aggregate(sessions));
        responseObserver.onCompleted();
    }

    private static GetRepositoryStatisticsResponse aggregate(List<StubDataset.Session> sessions) {
        GetRepositoryStatisticsResponse.Builder builder = GetRepositoryStatisticsResponse.newBuilder();

        long lastActivity = 0;
        long biggestSession = 0;
        boolean anyActive = false;

        for (StubDataset.Session session : sessions) {
            long sessionSize = 0;
            for (StubDataset.File file : session.files()) {
                accumulate(builder, file);
                builder.setTotalFiles(builder.getTotalFiles() + 1);
                builder.setTotalSize(builder.getTotalSize() + file.size());
                sessionSize += file.size();
            }
            biggestSession = Math.max(biggestSession, sessionSize);
            lastActivity = Math.max(lastActivity, lastActivityOf(session));
            anyActive = anyActive || session.active();
        }

        return builder
                .setTotalSessions(sessions.size())
                .setSessionStatus(anyActive ? RecordingStatus.RECORDING_STATUS_ACTIVE : RecordingStatus.RECORDING_STATUS_FINISHED)
                .setLastActivityTime(lastActivity)
                .setBiggestSessionSize(biggestSession)
                .build();
    }

    private static void accumulate(GetRepositoryStatisticsResponse.Builder builder, StubDataset.File file) {
        switch (file.kind()) {
            case JFR -> {
                builder.setJfrFiles(builder.getJfrFiles() + 1);
                builder.setJfrSize(builder.getJfrSize() + file.size());
            }
            case HEAP_DUMP -> {
                builder.setHeapDumpFiles(builder.getHeapDumpFiles() + 1);
                builder.setHeapDumpSize(builder.getHeapDumpSize() + file.size());
            }
            case GC_LOG -> {
                builder.setLogFiles(builder.getLogFiles() + 1);
                builder.setLogSize(builder.getLogSize() + file.size());
            }
            case APP_LOG -> {
                builder.setAppLogFiles(builder.getAppLogFiles() + 1);
                builder.setAppLogSize(builder.getAppLogSize() + file.size());
            }
            case HS_ERR_LOG -> {
                builder.setErrorLogFiles(builder.getErrorLogFiles() + 1);
                builder.setErrorLogSize(builder.getErrorLogSize() + file.size());
            }
            case OTHER -> {
                builder.setOtherFiles(builder.getOtherFiles() + 1);
                builder.setOtherSize(builder.getOtherSize() + file.size());
            }
        }
    }

    private static long lastActivityOf(StubDataset.Session session) {
        Instant activity = session.finishedAt() != null ? session.finishedAt() : session.createdAt();
        return activity.toEpochMilli();
    }
}
