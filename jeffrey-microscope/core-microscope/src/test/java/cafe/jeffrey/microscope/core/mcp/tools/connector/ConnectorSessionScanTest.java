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

package cafe.jeffrey.microscope.core.mcp.tools.connector;

import cafe.jeffrey.microscope.connector.api.DiscoveryClient;
import cafe.jeffrey.microscope.connector.api.manager.RepositoryManager;
import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.microscope.core.manager.project.ProjectsManager;
import cafe.jeffrey.microscope.core.manager.connector.ConnectorConnection;
import cafe.jeffrey.microscope.core.manager.connector.ConnectorManager;
import cafe.jeffrey.microscope.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.microscope.model.ProjectInfo;
import cafe.jeffrey.shared.common.exception.ErrorCode;
import cafe.jeffrey.shared.common.exception.JeffreyClientException;
import cafe.jeffrey.shared.common.exception.JeffreyInternalException;
import cafe.jeffrey.microscope.model.connector.AzureFilesAddress;
import cafe.jeffrey.microscope.model.connector.AzureFilesAuth;
import cafe.jeffrey.microscope.model.connector.AzureFilesProtocol;
import cafe.jeffrey.microscope.model.connector.ConnectorInfo;
import cafe.jeffrey.microscope.model.repository.RecordingSession;
import cafe.jeffrey.microscope.model.repository.RecordingSessionFilter;
import cafe.jeffrey.microscope.model.workspace.WorkspaceInfo;
import cafe.jeffrey.microscope.model.workspace.WorkspaceStatus;
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

class ConnectorSessionScanTest {

    private static final Instant NOW = Instant.parse("2026-03-01T12:00:00Z");
    private static final Duration BUDGET = Duration.ofSeconds(5);

    private final ConnectorManager connectorManager = mock(ConnectorManager.class);
    private final ConnectorSessionScan scan = new ConnectorSessionScan(connectorManager, BUDGET);

    private static ConnectorInfo connectorInfo() {
        return new ConnectorInfo("production", new AzureFilesAddress("https://jeffreyprod.file.core.windows.net",
                "recordings", "", AzureFilesProtocol.SMB, AzureFilesAuth.SAS, false));
    }

    private static WorkspaceInfo workspace(String id, String name) {
        return new WorkspaceInfo(id, "ref-" + id, "repo-" + id, name, null, null, NOW,
                WorkspaceStatus.AVAILABLE);
    }

    private static ProjectInfo projectInfo(String id, String name) {
        return new ProjectInfo(id, "origin-" + id, name, "ns", "ws-1", NOW, NOW, Map.of());
    }

    private static RecordingSession session(String id, Instant createdAt) {
        return new RecordingSession(id, id, "inst-1", createdAt, null,
                true, null, List.of(), false);
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

    /** The configured connector, answering its probe, with one workspace holding the given projects. */
    private ConnectorConnection configured(ProjectManager... projects) {
        WorkspaceInfo workspace = workspace("ws-1", "default");
        return configured(Map.of(workspace, workspaceHolding(projects)), List.of(workspace));
    }

    private ConnectorConnection configured(Map<WorkspaceInfo, WorkspaceManager> managers, List<WorkspaceInfo> order) {
        ConnectorConnection connector = mock(ConnectorConnection.class);
        when(connector.info()).thenReturn(connectorInfo());
        when(connector.infoOrThrow()).thenReturn(new DiscoveryClient.PublicApiInfo("1.0"));
        when(connector.workspaces()).thenReturn(order);
        when(connector.workspacesOrThrow()).thenReturn(order);
        managers.forEach((info, manager) -> when(connector.workspace(info)).thenReturn(manager));
        when(connectorManager.current()).thenReturn(Optional.of(connector));
        return connector;
    }

    private ConnectorConnection unreachable() {
        ConnectorConnection connector = mock(ConnectorConnection.class);
        when(connector.info()).thenReturn(connectorInfo());
        when(connector.infoOrThrow())
                .thenThrow(new JeffreyInternalException(ErrorCode.CONNECTOR_UNAVAILABLE, "connection refused"));
        when(connectorManager.current()).thenReturn(Optional.of(connector));
        return connector;
    }

    private static List<String> sessionIds(ConnectorSessionScan.Result result) {
        return result.rows().stream().map(row -> row.session().id()).toList();
    }

    /** Blocks the connector call until the scan cancels it, which interrupts the thread it runs on. */
    private static void awaitCancellation(CountDownLatch cancelled) {
        try {
            new CountDownLatch(1).await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            cancelled.countDown();
            Thread.currentThread().interrupt();
        }
    }

    @Nested
    class AcrossProjects {

        @Test
        void mergesRowsFromEveryProject() {
            configured(project("proj-1", "checkout", repositoryWith(session("s-1", NOW))),
                    project("proj-2", "search", repositoryWith(session("s-2", NOW))));

            ConnectorSessionScan.Result result = scan.scan(ConnectorScanFilter.ALL);

            assertEquals(2, result.rows().size());
            assertTrue(result.complete());
        }

        @Test
        void ordersNewestFirstAcrossProjects() {
            configured(project("proj-1", "checkout", repositoryWith(session("older", NOW.minusSeconds(600)))),
                    project("proj-2", "search", repositoryWith(session("newer", NOW))));

            assertEquals(List.of("newer", "older"), sessionIds(scan.scan(ConnectorScanFilter.ALL)));
        }

        @Test
        void carriesTheFullCoordinateOnEveryRow() {
            configured(project("proj-1", "checkout", repositoryWith(session("s-1", NOW))));

            ConnectorSessionScan.Row row = scan.scan(ConnectorScanFilter.ALL).rows().getFirst();

            assertEquals(new ConnectorSessionRef("ws-1", "proj-1", "s-1"), row.ref());
            assertEquals("default", row.workspaceName());
            assertEquals("checkout", row.projectName());
        }

        @Test
        void breaksTimestampTiesByTheFullSessionCoordinate() {
            configured(project("proj-2", "checkout", repositoryWith(session("a", NOW))),
                    project("proj-1", "search", repositoryWith(session("c", NOW), session("b", NOW))));

            ConnectorSessionScan.Result result = scan.scan(ConnectorScanFilter.ALL);

            assertEquals(List.of(new ConnectorSessionRef("ws-1", "proj-1", "b"),
                            new ConnectorSessionRef("ws-1", "proj-1", "c"),
                            new ConnectorSessionRef("ws-1", "proj-2", "a")),
                    result.rows().stream().map(ConnectorSessionScan.Row::ref).toList());
        }

        @Test
        void returnsNothingWhenNoConnectorIsConfigured() {
            when(connectorManager.current()).thenReturn(Optional.empty());

            ConnectorSessionScan.Result result = scan.scan(ConnectorScanFilter.ALL);

            assertTrue(result.rows().isEmpty());
            assertTrue(result.complete());
        }
    }

    @Nested
    class Probe {

        @Test
        void aConnectorThatAnswersIsReachableWithItsVersion() {
            ConnectorSessionScan.ProbeResult probe = scan.probe(configured());

            assertTrue(probe.reachable());
            assertEquals(Optional.of("1.0"), probe.version());
            assertTrue(probe.failures().isEmpty());
        }

        @Test
        void aConnectorThatDoesNotAnswerIsUnreachable() {
            ConnectorSessionScan.ProbeResult probe = scan.probe(unreachable());

            assertFalse(probe.reachable());
            assertEquals(ConnectorSessionScan.Failure.Kind.UNREACHABLE, probe.failures().getFirst().kind());
        }
    }

    @Nested
    class PartialFailure {

        @Test
        void stillReturnsRowsFromTheProjectsThatAnswered() {
            RepositoryManager failing = mock(RepositoryManager.class);
            when(failing.listRecordingSessions(any()))
                    .thenThrow(new JeffreyInternalException(ErrorCode.CONNECTOR_UNAVAILABLE, "connect refused"));
            configured(project("proj-1", "checkout", failing),
                    project("proj-2", "search", repositoryWith(session("s-1", NOW))));

            ConnectorSessionScan.Result result = scan.scan(ConnectorScanFilter.ALL);

            assertEquals(List.of("s-1"), sessionIds(result));
            assertEquals(1, result.failures().size());
            assertEquals("default/checkout", result.failures().getFirst().scope());
        }

        @Test
        void reportsAnUnreachableConnectorRatherThanLettingItLookEmpty() {
            // The managers below swallow their own errors, so without the probe a connector that is down
            // is indistinguishable from one with nothing on it.
            unreachable();

            ConnectorSessionScan.Result result = scan.scan(ConnectorScanFilter.ALL);

            assertTrue(result.rows().isEmpty());
            assertEquals("connector", result.failures().getFirst().scope());
            assertEquals("unreachable", result.failures().getFirst().reason());
        }

        @Test
        void neverDescendsIntoAConnectorThatDidNotAnswer() {
            ConnectorConnection connector = unreachable();

            scan.scan(ConnectorScanFilter.ALL);

            // workspaces() would return an empty list rather than failing, so the saving is real.
            verify(connector, never()).workspacesOrThrow();
        }

        @Test
        void reportsAnUnavailableProjectAsUnreachable() {
            RepositoryManager failing = mock(RepositoryManager.class);
            when(failing.listRecordingSessions(any()))
                    .thenThrow(new JeffreyInternalException(ErrorCode.CONNECTOR_UNAVAILABLE, "connect refused"));
            configured(project("proj-1", "checkout", failing));

            ConnectorSessionScan.Result result = scan.scan(ConnectorScanFilter.ALL);

            assertTrue(result.rows().isEmpty());
            assertEquals("unreachable", result.failures().getFirst().reason());
        }

        @Test
        void doesNotReportANotFoundAsUnreachable() {
            // A connector that answered and said no is a different problem with a different next step.
            RepositoryManager failing = mock(RepositoryManager.class);
            when(failing.listRecordingSessions(any()))
                    .thenThrow(new JeffreyClientException(ErrorCode.RESOURCE_NOT_FOUND, "project is gone"));
            configured(project("proj-1", "checkout", failing));

            String reason = scan.scan(ConnectorScanFilter.ALL).failures().getFirst().reason();

            assertTrue(reason.contains("project is gone"), reason);
        }

        @Test
        void keepsCompletedProjectRowsAndCancelsAnOverdueProjectRpc() throws Exception {
            CountDownLatch cancelled = new CountDownLatch(1);
            RepositoryManager slowRepository = mock(RepositoryManager.class);
            when(slowRepository.listRecordingSessions(any())).thenAnswer(_ -> {
                awaitCancellation(cancelled);
                throw new IllegalStateException("scan cancelled");
            });
            configured(project("fast-project", "fast", repositoryWith(session("fast-session", NOW))),
                    project("slow-project", "slow", slowRepository));

            ConnectorSessionScan.Result result =
                    new ConnectorSessionScan(connectorManager, Duration.ofMillis(100)).scan(ConnectorScanFilter.ALL);

            assertEquals(List.of("fast-session"), sessionIds(result));
            assertTrue(result.failures().stream()
                    .anyMatch(failure -> failure.scope().contains("slow")), result.failures().toString());
            assertTrue(cancelled.await(1, TimeUnit.SECONDS));
        }

        @Test
        void aStalledProbeIsCancelledAndReportedAsTheConnectorsDeadline() throws Exception {
            CountDownLatch cancelled = new CountDownLatch(1);
            ConnectorConnection stalled = mock(ConnectorConnection.class);
            when(stalled.info()).thenReturn(connectorInfo());
            when(stalled.infoOrThrow()).thenAnswer(_ -> {
                awaitCancellation(cancelled);
                throw new IllegalStateException("probe cancelled");
            });
            when(connectorManager.current()).thenReturn(Optional.of(stalled));

            ConnectorSessionScan.Result result =
                    new ConnectorSessionScan(connectorManager, Duration.ofMillis(150)).scan(ConnectorScanFilter.ALL);

            assertTrue(result.rows().isEmpty());
            ConnectorSessionScan.Failure failure = result.failures().getFirst();
            assertEquals("connector", failure.scope());
            assertEquals(ConnectorSessionScan.Failure.Kind.DEADLINE_EXCEEDED, failure.kind());
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
                throw new IllegalStateException("project listing cancelled");
            });
            WorkspaceManager stalledManager = mock(WorkspaceManager.class);
            when(stalledManager.projectsManager()).thenReturn(stalledProjects);

            configured(Map.of(fastWorkspace, fastManager, stalledWorkspace, stalledManager),
                    List.of(fastWorkspace, stalledWorkspace));

            ConnectorSessionScan.Result result =
                    new ConnectorSessionScan(connectorManager, Duration.ofMillis(150)).scan(ConnectorScanFilter.ALL);

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
        void pushesTheWindowDownToTheConnector() {
            RepositoryManager repo = repositoryWith(session("s-1", NOW));
            configured(project("proj-1", "checkout", repo));

            RecordingSessionFilter lastHour =
                    RecordingSessionFilter.activeWithinLast(Duration.ofHours(1), NOW);
            scan.scan(ConnectorScanFilter.ALL.withSessions(lastHour));

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
            ConnectorConnection connector = configured(Map.of(production, productionManager, staging, stagingManager),
                    List.of(production, staging));

            ConnectorSessionScan.Result result =
                    scan.scan(new ConnectorScanFilter("PROD", null, RecordingSessionFilter.ALL));

            assertEquals(List.of("s-1"), sessionIds(result));
            assertEquals("production", result.rows().getFirst().workspaceName());
            verify(connector, never()).workspace(eq(staging));
        }

        @Test
        void filtersProjectsByName() {
            configured(project("proj-1", "checkout", repositoryWith(session("s-1", NOW))));

            ConnectorSessionScan.Result result =
                    scan.scan(new ConnectorScanFilter(null, "search", RecordingSessionFilter.ALL));

            assertTrue(result.rows().isEmpty());
        }

        @Test
        void reportsAWorkspaceWhoseManagerCannotBeCreated() {
            WorkspaceInfo gone = workspace("ws-gone", "default");
            ConnectorConnection connector = configured(Map.of(), List.of(gone));
            when(connector.workspace(gone)).thenThrow(new IllegalStateException("workspace disappeared"));

            ConnectorSessionScan.Result result = scan.scan(ConnectorScanFilter.ALL);

            assertTrue(result.rows().isEmpty());
            assertTrue(result.failures().getFirst().reason().contains("workspace disappeared"));
        }
    }
}
