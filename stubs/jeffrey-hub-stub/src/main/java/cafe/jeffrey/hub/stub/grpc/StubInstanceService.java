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

import cafe.jeffrey.hub.api.v1.GetInstanceDetailRequest;
import cafe.jeffrey.hub.api.v1.GetInstanceDetailResponse;
import cafe.jeffrey.hub.api.v1.GetInstanceRequest;
import cafe.jeffrey.hub.api.v1.GetInstanceResponse;
import cafe.jeffrey.hub.api.v1.GetInstanceSessionDetailRequest;
import cafe.jeffrey.hub.api.v1.GetInstanceSessionDetailResponse;
import cafe.jeffrey.hub.api.v1.InstanceServiceGrpc;
import cafe.jeffrey.hub.api.v1.InstanceStats;
import cafe.jeffrey.hub.api.v1.ListInstanceSessionsRequest;
import cafe.jeffrey.hub.api.v1.ListInstanceSessionsResponse;
import cafe.jeffrey.hub.api.v1.ListInstancesRequest;
import cafe.jeffrey.hub.api.v1.ListInstancesResponse;
import cafe.jeffrey.hub.stub.data.StubDataset;
import cafe.jeffrey.hub.stub.data.StubSessionEnvironment;
import io.grpc.stub.StreamObserver;

import java.util.Optional;

/**
 * Stub {@code InstanceService} backed by the in-memory dataset. {@code GetInstanceDetail}
 * computes filesystem-style aggregates from the in-memory session files.
 */
public class StubInstanceService extends InstanceServiceGrpc.InstanceServiceImplBase {

    private final StubDataset dataset;

    public StubInstanceService(StubDataset dataset) {
        this.dataset = dataset;
    }

    @Override
    public void listInstances(ListInstancesRequest request, StreamObserver<ListInstancesResponse> responseObserver) {
        ListInstancesResponse.Builder builder = ListInstancesResponse.newBuilder();
        dataset.project(request.getProjectId())
                .ifPresent(project -> project.instances()
                        .forEach(instance -> builder.addInstances(
                                StubProtoMappers.instanceInfo(instance, request.getIncludeSessions()))));
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void getInstance(GetInstanceRequest request, StreamObserver<GetInstanceResponse> responseObserver) {
        dataset.instance(request.getInstanceId())
                .ifPresentOrElse(
                        instance -> {
                            responseObserver.onNext(GetInstanceResponse.newBuilder()
                                    .setInstance(StubProtoMappers.instanceInfo(instance, true))
                                    .build());
                            responseObserver.onCompleted();
                        },
                        () -> responseObserver.onError(
                                StubGrpcExceptions.notFound("Instance not found: " + request.getInstanceId())));
    }

    @Override
    public void listInstanceSessions(
            ListInstanceSessionsRequest request,
            StreamObserver<ListInstanceSessionsResponse> responseObserver) {

        ListInstanceSessionsResponse.Builder builder = ListInstanceSessionsResponse.newBuilder();
        dataset.instance(request.getInstanceId())
                .ifPresent(instance -> instance.sessions()
                        .forEach(session -> builder.addSessions(StubProtoMappers.instanceSessionInfo(session))));
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void getInstanceDetail(
            GetInstanceDetailRequest request,
            StreamObserver<GetInstanceDetailResponse> responseObserver) {

        dataset.instance(request.getInstanceId())
                .ifPresentOrElse(
                        instance -> {
                            responseObserver.onNext(GetInstanceDetailResponse.newBuilder()
                                    .setInstance(StubProtoMappers.instanceInfo(instance, true))
                                    .setStats(statsOf(instance))
                                    .build());
                            responseObserver.onCompleted();
                        },
                        () -> responseObserver.onError(
                                StubGrpcExceptions.notFound("Instance not found: " + request.getInstanceId())));
    }

    @Override
    public void getInstanceSessionDetail(
            GetInstanceSessionDetailRequest request,
            StreamObserver<GetInstanceSessionDetailResponse> responseObserver) {

        Optional<StubDataset.Session> session = dataset.instance(request.getInstanceId())
                .flatMap(instance -> instance.sessions().stream()
                        .filter(candidate -> candidate.id().equals(request.getSessionId()))
                        .findFirst());

        session.ifPresentOrElse(
                found -> {
                    responseObserver.onNext(GetInstanceSessionDetailResponse.newBuilder()
                            .setSession(StubProtoMappers.instanceSessionInfo(found))
                            .setEnvironmentJsonFields(StubSessionEnvironment.forSession(found))
                            .build());
                    responseObserver.onCompleted();
                },
                () -> responseObserver.onError(StubGrpcExceptions.notFound(
                        "Session not found: instanceId=" + request.getInstanceId()
                                + " sessionId=" + request.getSessionId())));
    }

    private static InstanceStats statsOf(StubDataset.Instance instance) {
        int fileCount = 0;
        long totalSize = 0;
        for (StubDataset.Session session : instance.sessions()) {
            for (StubDataset.File file : session.files()) {
                fileCount++;
                totalSize += file.size();
            }
        }
        return InstanceStats.newBuilder()
                .setFileCount(fileCount)
                .setTotalSizeBytes(totalSize)
                .build();
    }
}
