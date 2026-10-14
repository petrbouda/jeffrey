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

import cafe.jeffrey.hub.api.v1.GetSessionRequest;
import cafe.jeffrey.hub.api.v1.GetSessionResponse;
import cafe.jeffrey.hub.api.v1.InstanceInfo;
import cafe.jeffrey.hub.api.v1.InstanceServiceGrpc;
import cafe.jeffrey.hub.api.v1.InstanceStatus;
import cafe.jeffrey.hub.api.v1.ListInstancesRequest;
import cafe.jeffrey.hub.api.v1.ListInstancesResponse;
import cafe.jeffrey.hub.api.v1.ListSessionsRequest;
import cafe.jeffrey.hub.api.v1.ListSessionsResponse;
import cafe.jeffrey.hub.api.v1.RecordingSession;
import cafe.jeffrey.hub.api.v1.RecordingStatus;
import cafe.jeffrey.hub.api.v1.RepositoryFile;
import cafe.jeffrey.hub.api.v1.RepositoryServiceGrpc;
import cafe.jeffrey.hub.client.InstancesClient;
import cafe.jeffrey.hub.client.RepositoryClient;
import cafe.jeffrey.hub.client.dto.InstanceDetailResponse;
import cafe.jeffrey.hub.client.dto.InstanceSessionDetailResponse;
import cafe.jeffrey.hub.client.environment.SessionEnvironmentReader;
import cafe.jeffrey.microscope.grpc.client.GrpcHubConnection;
import cafe.jeffrey.microscope.model.ProjectInfo;
import cafe.jeffrey.microscope.model.ProjectInstanceInfo;
import cafe.jeffrey.microscope.model.ProjectInstanceInfo.ProjectInstanceStatus;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Instances as Microscope sees them: the lifecycle from the hub's instance listing, the sessions
 * under each from its session listing.
 */
class RemoteInstancesManagerTest {

    private static final String PROJECT_ID = "proj-1";
    private static final String LIVE_INSTANCE = "inst-live";
    private static final String EXPIRED_INSTANCE = "inst-expired";
    private static final Instant T0 = Instant.parse("2026-03-01T12:00:00Z");

    private static final ProjectInfo PROJECT =
            new ProjectInfo(PROJECT_ID, PROJECT_ID, "demo", null, "ws-1", T0, null, Map.of());

    /**
     * The live instance holds a finished session with one 1000-byte chunk and the session still
     * recording; the expired one has nothing left. Narrowed listings answer for one instance.
     */
    private static final class HubServices {

        private final AtomicReference<ListSessionsRequest> lastListing = new AtomicReference<>();

        private final InstanceServiceGrpc.InstanceServiceImplBase instances =
                new InstanceServiceGrpc.InstanceServiceImplBase() {
                    @Override
                    public void listInstances(
                            ListInstancesRequest request, StreamObserver<ListInstancesResponse> observer) {
                        observer.onNext(ListInstancesResponse.newBuilder()
                                .addInstances(InstanceInfo.newBuilder()
                                        .setId(LIVE_INSTANCE)
                                        .setStatus(InstanceStatus.INSTANCE_STATUS_ACTIVE)
                                        .setCreatedAt(T0.toEpochMilli())
                                        .setSessionCount(2))
                                .addInstances(InstanceInfo.newBuilder()
                                        .setId(EXPIRED_INSTANCE)
                                        .setStatus(InstanceStatus.INSTANCE_STATUS_EXPIRED)
                                        .setCreatedAt(T0.toEpochMilli())
                                        .setFinishedAt(T0.plusSeconds(60).toEpochMilli())
                                        .setExpiringAt(T0.plusSeconds(120).toEpochMilli())
                                        .setExpiredAt(T0.plusSeconds(180).toEpochMilli()))
                                .build());
                        observer.onCompleted();
                    }
                };

        private final RepositoryServiceGrpc.RepositoryServiceImplBase repository =
                new RepositoryServiceGrpc.RepositoryServiceImplBase() {
                    @Override
                    public void listSessions(ListSessionsRequest request, StreamObserver<ListSessionsResponse> observer) {
                        lastListing.set(request);
                        boolean everyInstance = !request.hasInstanceId();
                        ListSessionsResponse.Builder response = ListSessionsResponse.newBuilder();
                        if (everyInstance || LIVE_INSTANCE.equals(request.getInstanceId())) {
                            response.addSessions(liveSession()).addSessions(finishedSession());
                        }
                        observer.onNext(response.build());
                        observer.onCompleted();
                    }

                    @Override
                    public void getSession(GetSessionRequest request, StreamObserver<GetSessionResponse> observer) {
                        observer.onNext(GetSessionResponse.newBuilder().setSession(failedSession()).build());
                        observer.onCompleted();
                    }
                };

        private static RecordingSession liveSession() {
            return RecordingSession.newBuilder()
                    .setId("session-live")
                    .setInstanceId(LIVE_INSTANCE)
                    .setCreatedAt(T0.plusSeconds(60).toEpochMilli())
                    .setStatus(RecordingStatus.RECORDING_STATUS_ACTIVE)
                    .build();
        }

        private static RecordingSession finishedSession() {
            return RecordingSession.newBuilder()
                    .setId("session-finished")
                    .setInstanceId(LIVE_INSTANCE)
                    .setCreatedAt(T0.toEpochMilli())
                    .setFinishedAt(T0.plusSeconds(30).toEpochMilli())
                    .setStatus(RecordingStatus.RECORDING_STATUS_FINISHED)
                    .addFiles(RepositoryFile.newBuilder()
                            .setId("profile-1")
                            .setName("profile-1.jfr")
                            .setCreatedAt(T0.toEpochMilli())
                            .setSize(1000L)
                            .setIsRecording(true))
                    .build();
        }

        private static RecordingSession failedSession() {
            return RecordingSession.newBuilder()
                    .setId("session-failed")
                    .setInstanceId(LIVE_INSTANCE)
                    .setCreatedAt(T0.toEpochMilli())
                    .setFinishedAt(T0.plusSeconds(1).toEpochMilli())
                    .setStatus(RecordingStatus.RECORDING_STATUS_FINISHED)
                    .setFailed(true)
                    .build();
        }
    }

    private final HubServices hub = new HubServices();

    private Server server;
    private ManagedChannel channel;
    private RemoteInstancesManager manager;

    @BeforeEach
    void start() throws IOException {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor()
                .addService(hub.instances)
                .addService(hub.repository)
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        GrpcHubConnection connection = new GrpcHubConnection(channel) { };
        // The sessions served here hold no finished chunk to download, so the reader neither
        // looks a session up nor parses one
        SessionEnvironmentReader noEnvironment = new SessionEnvironmentReader(null, null);
        manager = new RemoteInstancesManager(
                PROJECT, new InstancesClient(connection), new RepositoryClient(connection), noEnvironment);
    }

    @AfterEach
    void stop() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Nested
    class FindAll {

        @Test
        void carriesTheLifecycleWithoutListingSessions() {
            List<ProjectInstanceInfo> instances = manager.findAll(false);

            assertEquals(2, instances.size());
            ProjectInstanceInfo expired = instances.get(1);
            assertEquals(ProjectInstanceStatus.EXPIRED, expired.status());
            assertEquals(T0.plusSeconds(120), expired.expiringAt());
            assertEquals(T0.plusSeconds(180), expired.expiredAt());
            assertTrue(instances.getFirst().sessions().isEmpty());
            assertNull(hub.lastListing.get(), "no session listing is asked for");
        }

        @Test
        void attachesEachInstanceItsSessions() {
            List<ProjectInstanceInfo> instances = manager.findAll(true);

            ProjectInstanceInfo live = instances.getFirst();
            assertEquals(2, live.sessions().size());
            assertEquals("session-live", live.activeSessionId());
            assertTrue(instances.get(1).sessions().isEmpty());
        }
    }

    @Nested
    class Detail {

        @Test
        void sumsTheInstanceFilesFromItsOwnListing() {
            InstanceDetailResponse detail = manager.detail(LIVE_INSTANCE).orElseThrow();

            assertEquals(LIVE_INSTANCE, hub.lastListing.get().getInstanceId());
            assertEquals(1, detail.stats().fileCount());
            assertEquals(1000L, detail.stats().totalSizeBytes());
            assertEquals(2, detail.instance().sessions().size());
            assertEquals("session-live", detail.instance().activeSessionId());
        }

        @Test
        void unknownInstanceHasNoDetail() {
            assertTrue(manager.detail("ghost").isEmpty());
        }
    }

    @Nested
    class SessionDetail {

        @Test
        void carriesTheFailedFlagTheHubReports() {
            InstanceSessionDetailResponse detail = manager.sessionDetail(LIVE_INSTANCE, "session-failed").orElseThrow();

            assertTrue(detail.session().failed());
            assertFalse(detail.session().isActive());
            assertNull(detail.environment());
        }

        @Test
        void aSessionOfAnotherInstanceIsNotFound() {
            Optional<InstanceSessionDetailResponse> detail = manager.sessionDetail(EXPIRED_INSTANCE, "session-failed");

            assertTrue(detail.isEmpty());
        }
    }
}
