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

import cafe.jeffrey.hub.api.v1.*;
import cafe.jeffrey.hub.core.HubJeffreyDirs;
import cafe.jeffrey.hub.core.activity.ActivityRequest;
import cafe.jeffrey.hub.core.activity.ActivityServiceFixtures;
import cafe.jeffrey.hub.core.activity.HubActivityService;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.core.streaming.ReplayStreamSubscription;
import cafe.jeffrey.hub.core.streaming.StreamingWindow;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
import cafe.jeffrey.hub.persistence.api.ProjectRepository;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.repository.RecordingSession;
import cafe.jeffrey.shared.common.model.repository.RepositoryFile;
import io.grpc.BindableService;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HubEventActivityGrpcTest {
    @Name("test.HubActivity")
    static class ActivityEvent extends Event {}

    @Test
    void countsOnHubThroughGrpcAndEnforcesScopeAndValidation(@TempDir Path temp) throws Exception {
        long start = Clock.systemUTC().millis();
        Path file = temp.resolve("activity.jfr");
        try (Recording recording = new Recording()) {
            recording.enable(ActivityEvent.class);
            recording.start();
            for (int i = 0; i < 1501; i++) {
                new ActivityEvent().commit();
            }
            recording.stop();
            recording.dump(file);
        }
        try (var context = new AnnotationConfigApplicationContext(
                GrpcServerConfigurationTest.MockDependencies.class, GrpcServerConfiguration.class)) {
            when(context.getBean(HubJeffreyDirs.class).temp()).thenReturn(temp.resolve("scratch"));
            HubPlatformRepositories repositories = context.getBean(HubPlatformRepositories.class);
            ProjectRepository projects = mock(ProjectRepository.class);
            when(repositories.newProjectRepository("project")).thenReturn(projects);
            ProjectInfo project = new ProjectInfo("project", null, null, null, null, "workspace", null, null, null, null);
            when(projects.find()).thenReturn(Optional.of(project));
            RepositoryStorage storage = mock(RepositoryStorage.class);
            when(context.getBean(RepositoryStorage.Factory.class).apply(project)).thenReturn(storage);
            RecordingSession session = mock(RecordingSession.class);
            RepositoryFile source = mock(RepositoryFile.class);
            when(source.isRecordingFile()).thenReturn(true);
            when(source.isFinished()).thenReturn(true);
            when(source.createdAt()).thenReturn(Instant.EPOCH);
            when(source.filePath()).thenReturn(file);
            when(session.files()).thenReturn(List.of(source));
            when(storage.singleSession("session", true)).thenReturn(Optional.of(session));
            String name = InProcessServerBuilder.generateName();
            var builder = InProcessServerBuilder.forName(name).directExecutor();
            context.getBeansOfType(BindableService.class).values().forEach(builder::addService);
            var server = builder.build().start();
            var channel = InProcessChannelBuilder.forName(name)
                    .directExecutor()
                    .build();
            try {
                var stub = EventActivityServiceGrpc.newBlockingStub(channel);
                var scope = ActivityScope.newBuilder()
                        .setWorkspaceId("workspace")
                        .setProjectId("project")
                        .setSessionId("session")
                        .build();
                var request = StartActivityRequest.newBuilder()
                        .setScope(scope)
                        .setStartTime(start)
                        .setEndTime(Clock.systemUTC().millis() + 1)
                        .addEventTypes("test.HubActivity")
                        .build();
                var initial = stub.startActivity(request);
                var poll = GetActivityRequest.newBuilder()
                        .setScope(scope)
                        .setScanId(initial.getScanId())
                        .setLimit(1)
                        .build();

                await().atMost(10, TimeUnit.SECONDS)
                        .until(() -> stub.getActivity(poll).hasFinishedAt());

                var result = stub.getActivity(poll);

                assertTrue(result.getComplete());
                assertEquals(1501, result.getTotalEvents());
                assertEquals(1501, result.getBuckets(0).getEventCount());
                assertEquals(0, result.getOffset());

                // Paging past the only populated bucket reaches the rest of the window.
                var second = stub.getActivity(poll.toBuilder().setOffset(1).build());
                assertEquals(1, second.getOffset());
                assertEquals(1501, second.getTotalEvents());
                assertTrue(second.getBucketsCount() <= 1);
                assertNotEquals(result.getBuckets(0).getStartTime(),
                        second.getBucketsCount() == 0 ? -1 : second.getBuckets(0).getStartTime());
                assertEquals(scope, result.getScope());
                assertEquals(1, result.getFilesTotal());
                assertEquals(ActivityState.ACTIVITY_STATE_COMPLETED, stub.cancelActivity(CancelActivityRequest.newBuilder()
                        .setScope(scope)
                        .setScanId(initial.getScanId())
                        .build()).getState());
                assertCode(Status.Code.NOT_FOUND, () -> stub.getActivity(poll.toBuilder()
                        .setScope(scope.toBuilder()
                                .setWorkspaceId("wrong"))
                        .build()));
                assertCode(Status.Code.NOT_FOUND, () -> stub.cancelActivity(CancelActivityRequest.newBuilder()
                        .setScope(scope.toBuilder()
                                .setSessionId("wrong"))
                        .setScanId(initial.getScanId())
                        .build()));
                assertCode(Status.Code.NOT_FOUND, () -> stub.getActivity(poll.toBuilder()
                        .setScanId("unknown")
                        .build()));
                assertCode(Status.Code.INVALID_ARGUMENT, () -> stub.getActivity(poll.toBuilder()
                        .setLimit(21)
                        .build()));
                assertCode(Status.Code.INVALID_ARGUMENT, () -> stub.startActivity(request.toBuilder().clearStartTime()
                        .build()));
                assertCode(Status.Code.INVALID_ARGUMENT, () -> stub.startActivity(request.toBuilder()
                        .setBucketSeconds(0)
                        .build()));
                assertCode(Status.Code.INVALID_ARGUMENT, () -> stub.getActivity(poll.toBuilder()
                        .setOffset(-1)
                        .build()));
                // An unknown scope is refused by StartActivity itself, so no scan ID is handed out
                // for a scan that could only ever report a failure.
                assertCode(Status.Code.NOT_FOUND, () -> stub.startActivity(request.toBuilder()
                        .setScope(scope.toBuilder().setSessionId("absent"))
                        .build()));

                verify(storage, never()).recordings(any(), any());
                verify(repositories, never()).findSessionWithRepositoryById(any());
            } finally {
                channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
                server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            }
        }
    }

    /**
     * What a refused {@code StartActivity} tells the caller to do next. {@code RESOURCE_EXHAUSTED}
     * means "wait or cancel a scan and try again", so it must be reserved for the two refusals where
     * that is true — a full retained-slot table and a stopping service — and never cover a failure
     * underneath the scope resolution, which no amount of waiting will clear.
     */
    @Nested
    class StartRefusals {

        private static final int MAX_RETAINED_SCANS = 16;

        private final ActivityScope scope = ActivityScope.newBuilder()
                .setWorkspaceId("workspace")
                .setProjectId("project")
                .setSessionId("session")
                .build();

        private StartActivityRequest start() {
            long now = Clock.systemUTC().millis();
            return StartActivityRequest.newBuilder()
                    .setScope(scope)
                    .setStartTime(now - 60_000)
                    .setEndTime(now + 1)
                    .build();
        }

        private ReplayStreamSubscription subscription(ActivityRequest request, Path temp) {
            return new ReplayStreamSubscription(
                    request.sessionId(),
                    List.of(),
                    Set.of(),
                    new StreamingWindow(Instant.ofEpochMilli(request.startTime()), Instant.ofEpochMilli(request.endTime())),
                    temp.resolve("scratch"),
                    request.workspaceId(),
                    request.projectId());
        }

        @Test
        void aBrokenScopeSourceIsNotReportedAsExhaustedCapacity() {
            Function<ActivityRequest, ReplayStreamSubscription> broken = _ -> {
                throw new IllegalStateException("storage broken");
            };
            try (var service = new HubActivityService(broken, Clock.systemUTC());
                 var grpc = InProcessGrpc.serving(new EventActivityGrpcService(service))) {
                var stub = EventActivityServiceGrpc.newBlockingStub(grpc.channel());

                StatusRuntimeException refusal =
                        assertThrows(StatusRuntimeException.class, () -> stub.startActivity(start()));

                assertNotEquals(Status.Code.RESOURCE_EXHAUSTED, refusal.getStatus().getCode(),
                        "a caller told to wait for a slot would wait forever");
                assertEquals(Status.Code.INTERNAL, refusal.getStatus().getCode(),
                        "an IllegalStateException that is not about capacity takes the generic mapping");
            }
        }

        @Test
        void aFullRetainedSlotTableIsReportedAsExhaustedCapacity(@TempDir Path temp) {
            try (var service = ActivityServiceFixtures.neverRunning(
                    request -> subscription(request, temp), Clock.systemUTC());
                 var grpc = InProcessGrpc.serving(new EventActivityGrpcService(service))) {
                var stub = EventActivityServiceGrpc.newBlockingStub(grpc.channel());
                for (int i = 0; i < MAX_RETAINED_SCANS; i++) {
                    assertFalse(stub.startActivity(start()).getScanId().isEmpty());
                }

                assertCode(Status.Code.RESOURCE_EXHAUSTED, () -> stub.startActivity(start()));
            }
        }
    }

    /** One service on an in-process server, torn down with the test. */
    private static final class InProcessGrpc implements AutoCloseable {

        private final Server server;
        private final ManagedChannel channel;

        private InProcessGrpc(Server server, ManagedChannel channel) {
            this.server = server;
            this.channel = channel;
        }

        static InProcessGrpc serving(BindableService service) {
            String name = InProcessServerBuilder.generateName();
            try {
                Server server = InProcessServerBuilder.forName(name)
                        .directExecutor()
                        .addService(service)
                        .build()
                        .start();
                ManagedChannel channel = InProcessChannelBuilder.forName(name).directExecutor().build();
                return new InProcessGrpc(server, channel);
            } catch (IOException e) {
                throw new IllegalStateException("Could not start the in-process gRPC server", e);
            }
        }

        ManagedChannel channel() {
            return channel;
        }

        @Override
        public void close() {
            channel.shutdownNow();
            server.shutdownNow();
        }
    }

    private static void assertCode(Status.Code code, Executable call) {
        assertEquals(code, assertThrows(StatusRuntimeException.class, call).getStatus().getCode());
    }
}
