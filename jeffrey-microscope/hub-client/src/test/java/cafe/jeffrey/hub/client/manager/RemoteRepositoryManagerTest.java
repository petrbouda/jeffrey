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

package cafe.jeffrey.hub.client.manager;

import cafe.jeffrey.hub.api.v1.ListSessionsRequest;
import cafe.jeffrey.hub.api.v1.ListSessionsResponse;
import cafe.jeffrey.hub.api.v1.RecordingSession;
import cafe.jeffrey.hub.api.v1.RecordingStatus;
import cafe.jeffrey.hub.api.v1.RepositoryFile;
import cafe.jeffrey.hub.api.v1.RepositoryServiceGrpc;
import cafe.jeffrey.hub.client.RepositoryClient;
import cafe.jeffrey.microscope.grpc.client.GrpcHubConnection;
import cafe.jeffrey.microscope.model.ProjectInfo;
import cafe.jeffrey.microscope.model.repository.RepositoryStatistics;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Repository statistics are one number — the bytes every file of every session occupies — summed
 * here over the session listing rather than asked of the hub separately.
 */
class RemoteRepositoryManagerTest {

    private static final String PROJECT_ID = "proj-1";
    private static final Instant T0 = Instant.parse("2026-03-01T12:00:00Z");

    private Server server;
    private ManagedChannel channel;
    private RemoteRepositoryManager manager;

    @BeforeEach
    void start() throws IOException {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor()
                .addService(new RepositoryServiceGrpc.RepositoryServiceImplBase() {
                    @Override
                    public void listSessions(ListSessionsRequest request, StreamObserver<ListSessionsResponse> observer) {
                        observer.onNext(ListSessionsResponse.newBuilder()
                                .addSessions(session("s-1", file("profile-1.jfr.lz4", 1_000L), file("heap.hprof", 20_000L)))
                                .addSessions(session("s-2", file("profile-2.jfr", 300L)))
                                .build());
                        observer.onCompleted();
                    }
                })
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        ProjectInfo project = new ProjectInfo(PROJECT_ID, PROJECT_ID, "demo", null, "ws-1", T0, null, Map.of());
        manager = new RemoteRepositoryManager(
                null, project, new RepositoryClient(new GrpcHubConnection(channel) { }), null);
    }

    @AfterEach
    void stop() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Test
    void sumsEveryFileOfEverySession() {
        assertEquals(new RepositoryStatistics(21_300L), manager.calculateRepositoryStatistics());
    }

    private static RecordingSession session(String id, RepositoryFile... files) {
        RecordingSession.Builder builder = RecordingSession.newBuilder()
                .setId(id)
                .setCreatedAt(T0.toEpochMilli())
                .setFinishedAt(T0.plusSeconds(60).toEpochMilli())
                .setStatus(RecordingStatus.RECORDING_STATUS_FINISHED);
        for (RepositoryFile file : files) {
            builder.addFiles(file);
        }
        return builder.build();
    }

    private static RepositoryFile file(String name, long size) {
        return RepositoryFile.newBuilder()
                .setId(name)
                .setName(name)
                .setCreatedAt(T0.toEpochMilli())
                .setSize(size)
                .setIsRecording(name.contains(".jfr"))
                .build();
    }
}
