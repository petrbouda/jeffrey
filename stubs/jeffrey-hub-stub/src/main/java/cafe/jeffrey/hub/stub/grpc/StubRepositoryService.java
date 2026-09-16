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
import cafe.jeffrey.hub.api.v1.RepositoryServiceGrpc;
import cafe.jeffrey.hub.stub.data.StubDataset;
import io.grpc.stub.StreamObserver;

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
        long totalSize = sessions.stream()
                .flatMap(session -> session.files().stream())
                .mapToLong(StubDataset.File::size)
                .sum();

        return GetRepositoryStatisticsResponse.newBuilder()
                .setTotalSize(totalSize)
                .build();
    }
}
