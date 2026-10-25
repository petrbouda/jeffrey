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

package cafe.jeffrey.microscope.core.mcp.tools.remote;

import cafe.jeffrey.microscope.remote.client.DiscoveryClient;
import cafe.jeffrey.microscope.remote.client.manager.RepositoryManager;
import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.microscope.core.manager.project.ProjectsManager;
import cafe.jeffrey.microscope.core.manager.remote.RemoteConnection;
import cafe.jeffrey.microscope.core.manager.remote.RemoteManager;
import cafe.jeffrey.microscope.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.microscope.model.ProjectInfo;
import cafe.jeffrey.microscope.model.remote.HubAddress;
import cafe.jeffrey.microscope.model.remote.RemoteInfo;
import cafe.jeffrey.microscope.model.repository.RecordingSession;
import cafe.jeffrey.microscope.model.repository.RecordingSessionFilter;
import cafe.jeffrey.microscope.model.repository.RecordingStatus;
import cafe.jeffrey.microscope.model.workspace.WorkspaceInfo;
import cafe.jeffrey.microscope.model.workspace.WorkspaceStatus;
import io.grpc.Status;
import io.grpc.Context;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class RemoteSessionScanTest {

    private static final Instant NOW = Instant.parse("2026-03-01T12:00:00Z");
    private static final Duration BUDGET = Duration.ofSeconds(5);

    private final RemoteManager remoteManager = mock(RemoteManager.class);
    private final RemoteSessionScan scan = new RemoteSessionScan(remoteManager, BUDGET);

    private static RemoteInfo remoteInfo() {
        return new RemoteInfo("production", new HubAddress("hub.example.com", 443, false));
    }

    private static WorkspaceInfo workspace(String id, String name) {
        return new WorkspaceInfo(id, "ref-" + id, "repo-" + id, name, null, null, NOW,
                WorkspaceStatus.AVAILABLE, 1);
    }

    private static ProjectInfo projectInfo(String id, String name) {
        return new ProjectInfo(id, "origin-" + id, name, "ns", "ws-1", NOW, NOW, Map.of());
    }

    private static RecordingSession session(String id, Instant createdAt) {
        return new RecordingSession(id, id, "inst-1", createdAt, null,
                RecordingStatus.ACTIVE, null, List.of(), false);
    }

    private static RepositoryManager repositoryWith(RecordingSession... sessions) {
        RepositoryManager repo = mock(RepositoryManager.class);
        when(repo.listRecordingSessions(any())).thenReturn(List.of(sessions));
        return repo;
    }

    private static ProjectManager project(String id, String name, RepositoryManager repository) {
        ProjectManager project = mock(ProjectManager.class);
        when(project.info()).thenReturn(projectInfo(id, name));
        when(project.repositoryManager()).thenReturn(repository);
        return project;
    }

    private static WorkspaceManager workspaceHolding(ProjectManager... projects) {
        ProjectsManager projectsManager = mock(ProjectsManager.class);
        when(projectsManager.findAll()).thenReturn(List.of(projects));
        when(projectsManager.findAllOrThrow()).thenReturn(List.of(projects));
        WorkspaceManager workspaceManager = mock(WorkspaceManager.class);
        when(workspaceManager.projectsManager()).thenReturn(projectsManager);
        return workspaceManager;
    }

    /** The connected remote, answering its probe, with one workspace holding the given projects. */
    private RemoteConnection connected(ProjectManager... projects) {
        WorkspaceInfo workspace = workspace("ws-1", "default");
        return connected(Map.of(workspace, workspaceHolding(projects)), List.of(workspace));
    }

    private RemoteConnection connected(Map<WorkspaceInfo, WorkspaceManager> managers, List<WorkspaceInfo> order) {
        RemoteConnection remote = mock(RemoteConnection.class);
        when(remote.info()).thenReturn(remoteInfo());
        when(remote.tryInfo()).thenReturn(Optional.of(new DiscoveryClient.PublicApiInfo("1.0")));
        when(remote.infoOrThrow()).thenReturn(new DiscoveryClient.PublicApiInfo("1.0"));
        when(remote.workspaces()).thenReturn(order);
        when(remote.workspacesOrThrow()).thenReturn(order);
        managers.forEach((info, manager) -> when(remote.workspace(info)).thenReturn(manager));
        when(remoteManager.current()).thenReturn(Optional.of(remote));
        return remote;
    }

    private RemoteConnection unreachable() {
        RemoteConnection remote = mock(RemoteConnection.class);
        when(remote.info()).thenReturn(remoteInfo());
        when(remote.tryInfo()).thenReturn(Optional.empty());
        when(remote.infoOrThrow())
                .thenThrow(Status.UNAVAILABLE.withDescription("connection refused").asRuntimeException());
        when(remoteManager.current()).thenReturn(Optional.of(remote));
        return remote;
    }

    private static List<String> sessionIds(RemoteSessionScan.Result result) {
        return result.rows().stream().map(row -> row.session().id()).toList();
    }

    private static void awaitCancellation(CountDownLatch cancelled) throws InterruptedException {
        Context.current().addListener(_ -> cancelled.countDown(), Runnable::run);
        cancelled.await(5, TimeUnit.SECONDS);
    }

    @Nested
    class AcrossProjects {

        @Test
        void mergesRowsFromEveryProject() {
            connected(project("proj-1", "checkout", repositoryWith(session("s-1", NOW))),
                    project("proj-2", "search", repositoryWith(session("s-2", NOW))));

            RemoteSessionScan.Result result = scan.scan(RemoteScanFilter.ALL, 0);

            assertEquals(2, result.rows().size());
            assertTrue(result.complete());
        }

        @Test
        void ordersNewestFirstAcrossProjects() {
            connected(project("proj-1", "checkout", repositoryWith(session("older", NOW.minusSeconds(600)))),
                    project("proj-2", "search", repositoryWith(session("newer", NOW))));

            assertEquals(List.of("newer", "older"), sessionIds(scan.scan(RemoteScanFilter.ALL, 0)));
        }

        @Test
        void carriesTheFullCoordinateOnEveryRow() {
            connected(project("proj-1", "checkout", repositoryWith(session("s-1", NOW))));

            RemoteSessionScan.Row row = scan.scan(RemoteScanFilter.ALL, 0).rows().getFirst();

            assertEquals(new RemoteSessionRef("ws-1", "proj-1", "s-1"), row.ref());
            assertEquals("default", row.workspaceName());
            assertEquals("checkout", row.projectName());
        }

        @Test
        void breaksTimestampTiesByTheFullSessionCoordinate() {
            connected(project("proj-2", "checkout", repositoryWith(session("a", NOW))),
                    project("proj-1", "search", repositoryWith(session("c", NOW), session("b", NOW))));

            RemoteSessionScan.Result result = scan.scan(RemoteScanFilter.ALL, 0);

            assertEquals(List.of(new RemoteSessionRef("ws-1", "proj-1", "b"),
                            new RemoteSessionRef("ws-1", "proj-1", "c"),
                            new RemoteSessionRef("ws-1", "proj-2", "a")),
                    result.rows().stream().map(RemoteSessionScan.Row::ref).toList());
        }

        @Test
        void appliesTheGlobalLimitAfterTheMerge() {
            // The remote caps per project, so a merge across projects can still exceed what was asked for.
            connected(project("proj-1", "checkout", repositoryWith(session("newest", NOW))),
                    project("proj-2", "search", repositoryWith(session("older", NOW.minusSeconds(60)))));

            RemoteSessionScan.Result result = scan.scan(RemoteScanFilter.ALL, 1);

            assertEquals(List.of("newest"), sessionIds(result));
        }

        @Test
        void returnsNothingWhenNoRemoteIsConnected() {
            when(remoteManager.current()).thenReturn(Optional.empty());

            RemoteSessionScan.Result result = scan.scan(RemoteScanFilter.ALL, 0);

            assertTrue(result.rows().isEmpty());
            assertTrue(result.complete());
        }
    }

    @Nested
    class Probe {

        @Test
        void aRemoteThatAnswersIsReachableWithItsVersion() {
            RemoteSessionScan.ProbeResult probe = scan.probe(connected());

            assertTrue(probe.reachable());
            assertEquals(Optional.of("1.0"), probe.version());
            assertTrue(probe.failures().isEmpty());
        }

        @Test
        void aRemoteThatDoesNotAnswerIsUnreachable() {
            RemoteSessionScan.ProbeResult probe = scan.probe(unreachable());

            assertFalse(probe.reachable());
            assertEquals(RemoteSessionScan.Failure.Kind.UNREACHABLE, probe.failures().getFirst().kind());
        }
    }

    @Nested
    class PartialFailure {

        @Test
        void stillReturnsRowsFromTheProjectsThatAnswered() {
            RepositoryManager failing = mock(RepositoryManager.class);
            when(failing.listRecordingSessions(any()))
                    .thenThrow(Status.UNAVAILABLE.withDescription("connect refused").asRuntimeException());
            connected(project("proj-1", "checkout", failing),
                    project("proj-2", "search", repositoryWith(session("s-1", NOW))));

            RemoteSessionScan.Result result = scan.scan(RemoteScanFilter.ALL, 0);

            assertEquals(List.of("s-1"), sessionIds(result));
            assertEquals(1, result.failures().size());
            assertEquals("default/checkout", result.failures().getFirst().scope());
        }

        @Test
        void reportsAnUnreachableRemoteRatherThanLettingItLookEmpty() {
            // The managers below swallow their own errors, so without the probe a remote that is down
            // is indistinguishable from one with nothing on it.
            unreachable();

            RemoteSessionScan.Result result = scan.scan(RemoteScanFilter.ALL, 0);

            assertTrue(result.rows().isEmpty());
            assertEquals("remote", result.failures().getFirst().scope());
            assertEquals("unreachable", result.failures().getFirst().reason());
        }

        @Test
        void neverDescendsIntoARemoteThatDidNotAnswer() {
            RemoteConnection remote = unreachable();

            scan.scan(RemoteScanFilter.ALL, 0);

            // workspaces() would return an empty list rather than failing, so the saving is real.
            verify(remote, never()).workspacesOrThrow();
        }

        @Test
        void reportsAGrpcUnavailableFromOneProjectAsUnreachable() {
            RepositoryManager failing = mock(RepositoryManager.class);
            when(failing.listRecordingSessions(any()))
                    .thenThrow(Status.UNAVAILABLE.withDescription("connect refused").asRuntimeException());
            connected(project("proj-1", "checkout", failing));

            RemoteSessionScan.Result result = scan.scan(RemoteScanFilter.ALL, 0);

            assertTrue(result.rows().isEmpty());
            assertEquals("unreachable", result.failures().getFirst().reason());
        }

        @Test
        void doesNotReportANotFoundAsUnreachable() {
            // A remote that answered and said no is a different problem with a different next step.
            RepositoryManager failing = mock(RepositoryManager.class);
            when(failing.listRecordingSessions(any()))
                    .thenThrow(Status.NOT_FOUND.withDescription("project is gone").asRuntimeException());
            connected(project("proj-1", "checkout", failing));

            String reason = scan.scan(RemoteScanFilter.ALL, 0).failures().getFirst().reason();

            assertTrue(reason.contains("project is gone"), reason);
        }

        @Test
        void reportsADeadlineAsADeadlineRatherThanAnUnreachableRemote() {
            RepositoryManager failing = mock(RepositoryManager.class);
            when(failing.listRecordingSessions(any()))
                    .thenThrow(Status.DEADLINE_EXCEEDED.withDescription("scan budget elapsed").asRuntimeException());
            connected(project("proj-1", "checkout", failing));

            String reason = scan.scan(RemoteScanFilter.ALL, 0).failures().getFirst().reason();

            assertTrue(reason.contains("deadline"), reason);
        }

        @Test
        void keepsCompletedProjectRowsAndCancelsAnOverdueProjectRpc() throws Exception {
            CountDownLatch cancelled = new CountDownLatch(1);
            RepositoryManager slowRepository = mock(RepositoryManager.class);
            when(slowRepository.listRecordingSessions(any())).thenAnswer(_ -> {
                awaitCancellation(cancelled);
                throw Status.CANCELLED.withDescription("scan cancelled").asRuntimeException();
            });
            connected(project("fast-project", "fast", repositoryWith(session("fast-session", NOW))),
                    project("slow-project", "slow", slowRepository));

            RemoteSessionScan.Result result =
                    new RemoteSessionScan(remoteManager, Duration.ofMillis(100)).scan(RemoteScanFilter.ALL, 0);

            assertEquals(List.of("fast-session"), sessionIds(result));
            assertTrue(result.failures().stream()
                    .anyMatch(failure -> failure.scope().contains("slow")), result.failures().toString());
            assertTrue(cancelled.await(1, TimeUnit.SECONDS));
        }

        @Test
        void aStalledProbeIsCancelledAndReportedAsTheRemotesDeadline() throws Exception {
            CountDownLatch cancelled = new CountDownLatch(1);
            RemoteConnection stalled = mock(RemoteConnection.class);
            when(stalled.info()).thenReturn(remoteInfo());
            when(stalled.infoOrThrow()).thenAnswer(_ -> {
                awaitCancellation(cancelled);
                throw Status.CANCELLED.withDescription("probe cancelled").asRuntimeException();
            });
            when(remoteManager.current()).thenReturn(Optional.of(stalled));

            RemoteSessionScan.Result result =
                    new RemoteSessionScan(remoteManager, Duration.ofMillis(150)).scan(RemoteScanFilter.ALL, 0);

            assertTrue(result.rows().isEmpty());
            RemoteSessionScan.Failure failure = result.failures().getFirst();
            assertEquals("remote", failure.scope());
            assertEquals(RemoteSessionScan.Failure.Kind.DEADLINE_EXCEEDED, failure.kind());
            assertTrue(cancelled.await(1, TimeUnit.SECONDS));
        }

        @Test
        void fastWorkspaceReachesSessionsWhileAnotherWorkspaceProjectListIsStalled() throws Exception {
            CountDownLatch cancelled = new CountDownLatch(1);
            WorkspaceInfo fastWorkspace = workspace("ws-fast", "fast-workspace");
            WorkspaceInfo stalledWorkspace = workspace("ws-stalled", "stalled-workspace");

            WorkspaceManager fastManager = workspaceHolding(
                    project("fast-project", "fast-project", repositoryWith(session("fast-session", NOW))));

            ProjectsManager stalledProjects = mock(ProjectsManager.class);
            when(stalledProjects.findAllOrThrow()).thenAnswer(_ -> {
                awaitCancellation(cancelled);
                throw Status.CANCELLED.withDescription("project listing cancelled").asRuntimeException();
            });
            WorkspaceManager stalledManager = mock(WorkspaceManager.class);
            when(stalledManager.projectsManager()).thenReturn(stalledProjects);

            connected(Map.of(fastWorkspace, fastManager, stalledWorkspace, stalledManager),
                    List.of(fastWorkspace, stalledWorkspace));

            RemoteSessionScan.Result result =
                    new RemoteSessionScan(remoteManager, Duration.ofMillis(150)).scan(RemoteScanFilter.ALL, 0);

            assertEquals(List.of("fast-session"), sessionIds(result));
            assertTrue(result.failures().stream()
                            .anyMatch(failure -> failure.scope().contains("stalled-workspace")),
                    result.failures().toString());
            assertTrue(cancelled.await(1, TimeUnit.SECONDS));
        }
    }

    @Nested
    class Filtering {

        @Test
        void pushesTheWindowDownToTheRemote() {
            RepositoryManager repo = repositoryWith(session("s-1", NOW));
            connected(project("proj-1", "checkout", repo));

            RecordingSessionFilter lastHour =
                    RecordingSessionFilter.activeWithinLast(Duration.ofHours(1), NOW);
            scan.scan(RemoteScanFilter.ALL.withSessions(lastHour), 0);

            ArgumentCaptor<RecordingSessionFilter> captor =
                    ArgumentCaptor.forClass(RecordingSessionFilter.class);
            verify(repo).listRecordingSessions(captor.capture());
            assertEquals(NOW.minus(Duration.ofHours(1)), captor.getValue().activeFrom());
        }

        @Test
        void filtersWorkspacesByANameFragmentAndNeverListsTheOthers() {
            WorkspaceInfo production = workspace("ws-1", "production");
            WorkspaceInfo staging = workspace("ws-2", "staging");
            WorkspaceManager productionManager =
                    workspaceHolding(project("proj-1", "checkout", repositoryWith(session("s-1", NOW))));
            WorkspaceManager stagingManager =
                    workspaceHolding(project("proj-2", "search", repositoryWith(session("s-2", NOW))));
            RemoteConnection remote = connected(Map.of(production, productionManager, staging, stagingManager),
                    List.of(production, staging));

            RemoteSessionScan.Result result =
                    scan.scan(new RemoteScanFilter("PROD", null, RecordingSessionFilter.ALL), 0);

            assertEquals(List.of("s-1"), sessionIds(result));
            assertEquals("production", result.rows().getFirst().workspaceName());
            verify(remote, never()).workspace(eq(staging));
        }

        @Test
        void filtersProjectsByName() {
            connected(project("proj-1", "checkout", repositoryWith(session("s-1", NOW))));

            RemoteSessionScan.Result result =
                    scan.scan(new RemoteScanFilter(null, "search", RecordingSessionFilter.ALL), 0);

            assertTrue(result.rows().isEmpty());
        }

        @Test
        void reportsAWorkspaceWhoseManagerCannotBeCreated() {
            WorkspaceInfo gone = workspace("ws-gone", "default");
            RemoteConnection remote = connected(Map.of(), List.of(gone));
            when(remote.workspace(gone)).thenThrow(new IllegalStateException("workspace disappeared"));

            RemoteSessionScan.Result result = scan.scan(RemoteScanFilter.ALL, 0);

            assertTrue(result.rows().isEmpty());
            assertTrue(result.failures().getFirst().reason().contains("workspace disappeared"));
        }
    }
}
