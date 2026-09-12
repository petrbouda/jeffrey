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

package cafe.jeffrey.microscope.core.mcp.tools.hubs;

import cafe.jeffrey.hub.client.DiscoveryClient;
import cafe.jeffrey.hub.client.manager.RepositoryManager;
import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.microscope.core.manager.project.ProjectsManager;
import cafe.jeffrey.microscope.core.manager.hub.HubManager;
import cafe.jeffrey.microscope.core.manager.hub.HubsManager;
import cafe.jeffrey.microscope.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.hub.HubAddress;
import cafe.jeffrey.shared.common.model.hub.HubInfo;
import cafe.jeffrey.shared.common.model.hub.HubSource;
import cafe.jeffrey.shared.common.model.repository.RecordingSession;
import cafe.jeffrey.shared.common.model.repository.RecordingSessionFilter;
import cafe.jeffrey.shared.common.model.repository.RecordingStatus;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceStatus;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class HubSessionScanTest {

    private static final Instant NOW = Instant.parse("2026-03-01T12:00:00Z");
    private static final Duration BUDGET = Duration.ofSeconds(5);

    private final HubsManager hubsManager = mock(HubsManager.class);
    private final HubSessionScan scan = new HubSessionScan(hubsManager, BUDGET);

    private static HubInfo hubInfo(String id, String name) {
        return new HubInfo(id, name, new HubAddress("hub.example.com", 443, false), NOW, HubSource.CONFIG);
    }

    private static WorkspaceInfo workspace(String id, String name) {
        return new WorkspaceInfo(id, "ref-" + id, "repo-" + id, name, null, null, NOW,
                WorkspaceStatus.AVAILABLE, 1);
    }

    private static ProjectInfo projectInfo(String id, String name) {
        return new ProjectInfo(id, "origin-" + id, name, name, "ns", "ws-1", NOW, NOW, Map.of(), null);
    }

    private static RecordingSession session(String id, Instant createdAt) {
        return new RecordingSession(id, id, "inst-1", createdAt, null,
                RecordingStatus.ACTIVE, null, null, List.of(), false);
    }

    /**
     * A hub that answers its probe, with one workspace holding one project.
     */
    private HubManager reachableHub(String hubId, String hubName, String projectName, RepositoryManager repo) {
        HubManager hub = mock(HubManager.class);
        when(hub.info()).thenReturn(hubInfo(hubId, hubName));
        when(hub.tryInfo()).thenReturn(Optional.of(new DiscoveryClient.PublicApiInfo("1.0", 1)));
        when(hub.infoOrThrow()).thenReturn(new DiscoveryClient.PublicApiInfo("1.0", 1));
        WorkspaceInfo workspace = workspace("ws-1", "default");
        when(hub.workspaces()).thenReturn(List.of(workspace));
        when(hub.workspacesOrThrow()).thenReturn(List.of(workspace));

        ProjectManager project = mock(ProjectManager.class);
        when(project.info()).thenReturn(projectInfo("proj-1", projectName));
        when(project.repositoryManager()).thenReturn(repo);

        ProjectsManager projects = mock(ProjectsManager.class);
        when(projects.findAll()).thenReturn(List.of(project));
        when(projects.findAllOrThrow()).thenReturn(List.of(project));

        WorkspaceManager workspaceManager = mock(WorkspaceManager.class);
        when(workspaceManager.projectsManager()).thenReturn(projects);
        when(hub.workspace("ws-1")).thenReturn(Optional.of(workspaceManager));
        when(hub.workspace(workspace)).thenReturn(workspaceManager);
        return hub;
    }

    private static RepositoryManager repositoryWith(RecordingSession... sessions) {
        RepositoryManager repo = mock(RepositoryManager.class);
        when(repo.listRecordingSessions(anyBoolean(), any())).thenReturn(List.of(sessions));
        return repo;
    }

    @Nested
    class AcrossHubs {

        @Test
        void mergesRowsFromEveryHub() {
            HubManager production = reachableHub("h-1", "production", "checkout",
                    repositoryWith(session("s-1", NOW)));
            HubManager staging = reachableHub("h-2", "staging", "search",
                    repositoryWith(session("s-2", NOW)));
            when(hubsManager.findAll()).thenReturn(List.of(production, staging));

            HubSessionScan.Result result = scan.scan(HubScanFilter.ALL, 0);

            assertEquals(2, result.rows().size());
            assertTrue(result.complete());
        }

        @Test
        void ordersNewestFirstAcrossHubs() {
            HubManager production = reachableHub("h-1", "production", "checkout",
                    repositoryWith(session("older", NOW.minusSeconds(600))));
            HubManager staging = reachableHub("h-2", "staging", "search",
                    repositoryWith(session("newer", NOW)));
            when(hubsManager.findAll()).thenReturn(List.of(production, staging));

            HubSessionScan.Result result = scan.scan(HubScanFilter.ALL, 0);

            assertEquals(List.of("newer", "older"),
                    result.rows().stream().map(row -> row.session().id()).toList());
        }

        @Test
        void carriesTheFullCoordinateOnEveryRow() {
            HubManager production = reachableHub("h-1", "production", "checkout",
                    repositoryWith(session("s-1", NOW)));
            when(hubsManager.findAll()).thenReturn(List.of(production));

            HubSessionScan.Row row = scan.scan(HubScanFilter.ALL, 0).rows().getFirst();

            assertEquals(new HubSessionRef("h-1", "ws-1", "proj-1", "s-1"), row.ref());
            assertEquals("production", row.hubName());
            assertEquals("checkout", row.projectName());
        }

        @Test
        void appliesTheGlobalLimitAfterTheMerge() {
            // The hub caps per project, so a merge across hubs can still exceed what was asked for.
            HubManager production = reachableHub("h-1", "production", "checkout",
                    repositoryWith(session("newest", NOW)));
            HubManager staging = reachableHub("h-2", "staging", "search",
                    repositoryWith(session("older", NOW.minusSeconds(60))));
            when(hubsManager.findAll()).thenReturn(List.of(production, staging));

            HubSessionScan.Result result = scan.scan(HubScanFilter.ALL, 1);

            assertEquals(1, result.rows().size());
            assertEquals("newest", result.rows().getFirst().session().id());
        }

        @Test
        void returnsNothingWhenNoHubIsConfigured() {
            when(hubsManager.findAll()).thenReturn(List.of());

            HubSessionScan.Result result = scan.scan(HubScanFilter.ALL, 0);

            assertTrue(result.rows().isEmpty());
            assertTrue(result.complete());
        }
    }

    @Nested
    class PartialFailure {

        private HubManager unreachableHub(String hubId, String hubName) {
            HubManager hub = mock(HubManager.class);
            when(hub.info()).thenReturn(hubInfo(hubId, hubName));
            when(hub.tryInfo()).thenReturn(Optional.empty());
            when(hub.infoOrThrow())
                    .thenThrow(Status.UNAVAILABLE.withDescription("connection refused").asRuntimeException());
            return hub;
        }

        @Test
        void stillReturnsRowsFromTheHubsThatAnswered() {
            HubManager down = unreachableHub("h-down", "production");
            HubManager up = reachableHub("h-up", "staging", "search",
                    repositoryWith(session("s-1", NOW)));
            when(hubsManager.findAll()).thenReturn(List.of(down, up));

            HubSessionScan.Result result = scan.scan(HubScanFilter.ALL, 0);

            assertEquals(1, result.rows().size());
            assertEquals(1, result.failures().size());
            assertEquals("production", result.failures().getFirst().hubName());
        }

        @Test
        void reportsAnUnreachableHubRatherThanLettingItLookEmpty() {
            // The managers below swallow their own errors, so without the probe a hub that is down
            // is indistinguishable from one with nothing on it.
            HubManager down = unreachableHub("h-1", "production");
            when(hubsManager.findAll()).thenReturn(List.of(down));

            HubSessionScan.Result result = scan.scan(HubScanFilter.ALL, 0);

            assertTrue(result.rows().isEmpty());
            assertEquals("unreachable", result.failures().getFirst().reason());
        }

        @Test
        void neverDescendsIntoAHubThatDidNotAnswer() {
            HubManager hub = unreachableHub("h-1", "production");
            when(hubsManager.findAll()).thenReturn(List.of(hub));

            scan.scan(HubScanFilter.ALL, 0);

            // workspaces() would return an empty list rather than failing, so the saving is real.
            verify(hub, never()).workspacesOrThrow();
        }

        @Test
        void reportsAGrpcUnavailableFromOneProjectAsUnreachable() {
            RepositoryManager failing = mock(RepositoryManager.class);
            when(failing.listRecordingSessions(anyBoolean(), any()))
                    .thenThrow(Status.UNAVAILABLE.withDescription("connect refused").asRuntimeException());
            HubManager production = reachableHub("h-1", "production", "checkout", failing);
            when(hubsManager.findAll()).thenReturn(List.of(production));

            HubSessionScan.Result result = scan.scan(HubScanFilter.ALL, 0);

            assertTrue(result.rows().isEmpty());
            assertEquals("unreachable", result.failures().getFirst().reason());
        }

        @Test
        void doesNotReportANotFoundAsUnreachable() {
            // A hub that answered and said no is a different problem with a different next step.
            RepositoryManager failing = mock(RepositoryManager.class);
            when(failing.listRecordingSessions(anyBoolean(), any()))
                    .thenThrow(Status.NOT_FOUND.withDescription("project is gone").asRuntimeException());
            HubManager production = reachableHub("h-1", "production", "checkout", failing);
            when(hubsManager.findAll()).thenReturn(List.of(production));

            HubSessionScan.Result result = scan.scan(HubScanFilter.ALL, 0);

            String reason = result.failures().getFirst().reason();
            assertTrue(reason.contains("project is gone"), reason);
        }

        @Test
        void reportsADeadlineAsADeadlineRatherThanAnUnreachableHub() {
            RepositoryManager failing = mock(RepositoryManager.class);
            when(failing.listRecordingSessions(anyBoolean(), any()))
                    .thenThrow(Status.DEADLINE_EXCEEDED.withDescription("scan budget elapsed").asRuntimeException());
            HubManager production = reachableHub("h-1", "production", "checkout", failing);
            when(hubsManager.findAll()).thenReturn(List.of(production));

            HubSessionScan.Result result = scan.scan(HubScanFilter.ALL, 0);

            String reason = result.failures().getFirst().reason();
            assertTrue(reason.contains("deadline"), reason);
        }

        @Test
        void keepsCompletedProjectRowsAndCancelsAnOverdueProjectRpc() throws Exception {
            CountDownLatch cancelled = new CountDownLatch(1);

            RepositoryManager fastRepository = repositoryWith(session("fast-session", NOW));
            RepositoryManager slowRepository = mock(RepositoryManager.class);
            when(slowRepository.listRecordingSessions(anyBoolean(), any())).thenAnswer(_ -> {
                Context.current().addListener(_ -> cancelled.countDown(), Runnable::run);
                cancelled.await(5, TimeUnit.SECONDS);
                throw Status.CANCELLED.withDescription("scan cancelled").asRuntimeException();
            });

            ProjectManager fastProject = project("fast-project", "fast", fastRepository);
            ProjectManager slowProject = project("slow-project", "slow", slowRepository);
            HubManager hub = hubWithProjects(fastProject, slowProject);
            when(hubsManager.findAll()).thenReturn(List.of(hub));

            HubSessionScan.Result result =
                    new HubSessionScan(hubsManager, Duration.ofMillis(100)).scan(HubScanFilter.ALL, 0);

            assertEquals(List.of("fast-session"),
                    result.rows().stream().map(row -> row.session().id()).toList());
            assertTrue(result.failures().stream()
                    .anyMatch(failure -> failure.scope().contains("slow")), result.failures().toString());
            assertTrue(cancelled.await(1, TimeUnit.SECONDS));
        }

        @Test
        void fastHubReachesSessionsWhileAnotherHubProbeIsStillStalled() throws Exception {
            CountDownLatch cancelled = new CountDownLatch(1);
            HubManager fast = reachableHub(
                    "h-fast", "staging", "search", repositoryWith(session("fast-session", NOW)));
            HubManager stalled = mock(HubManager.class);
            when(stalled.info()).thenReturn(hubInfo("h-stalled", "production"));
            when(stalled.infoOrThrow()).thenAnswer(_ -> {
                awaitCancellation(cancelled);
                throw Status.CANCELLED.withDescription("probe cancelled").asRuntimeException();
            });
            when(hubsManager.findAll()).thenReturn(List.of(stalled, fast));

            HubSessionScan.Result result =
                    new HubSessionScan(hubsManager, Duration.ofMillis(150)).scan(HubScanFilter.ALL, 0);

            assertEquals(List.of("fast-session"),
                    result.rows().stream().map(row -> row.session().id()).toList());
            assertTrue(result.failures().stream()
                    .anyMatch(failure -> failure.scope().equals("production")), result.failures().toString());
            assertTrue(cancelled.await(1, TimeUnit.SECONDS));
        }

        @Test
        void fastWorkspaceReachesSessionsWhileAnotherWorkspaceProjectListIsStalled() throws Exception {
            CountDownLatch cancelled = new CountDownLatch(1);
            WorkspaceInfo fastWorkspace = workspace("ws-fast", "fast-workspace");
            WorkspaceInfo stalledWorkspace = workspace("ws-stalled", "stalled-workspace");

            ProjectsManager fastProjects = mock(ProjectsManager.class);
            RepositoryManager fastRepository = repositoryWith(session("fast-session", NOW));
            ProjectManager fastProject = project("fast-project", "fast-project", fastRepository);
            when(fastProjects.findAllOrThrow()).thenReturn(List.of(fastProject));
            WorkspaceManager fastManager = mock(WorkspaceManager.class);
            when(fastManager.projectsManager()).thenReturn(fastProjects);

            ProjectsManager stalledProjects = mock(ProjectsManager.class);
            when(stalledProjects.findAllOrThrow()).thenAnswer(_ -> {
                awaitCancellation(cancelled);
                throw Status.CANCELLED.withDescription("project listing cancelled").asRuntimeException();
            });
            WorkspaceManager stalledManager = mock(WorkspaceManager.class);
            when(stalledManager.projectsManager()).thenReturn(stalledProjects);

            HubManager hub = mock(HubManager.class);
            when(hub.info()).thenReturn(hubInfo("h-1", "production"));
            when(hub.infoOrThrow()).thenReturn(new DiscoveryClient.PublicApiInfo("1.0", 1));
            when(hub.workspacesOrThrow()).thenReturn(List.of(fastWorkspace, stalledWorkspace));
            when(hub.workspace(fastWorkspace)).thenReturn(fastManager);
            when(hub.workspace(stalledWorkspace)).thenReturn(stalledManager);
            when(hubsManager.findAll()).thenReturn(List.of(hub));

            HubSessionScan.Result result =
                    new HubSessionScan(hubsManager, Duration.ofMillis(150)).scan(HubScanFilter.ALL, 0);

            assertEquals(List.of("fast-session"),
                    result.rows().stream().map(row -> row.session().id()).toList());
            assertTrue(result.failures().stream()
                    .anyMatch(failure -> failure.scope().contains("stalled-workspace")),
                    result.failures().toString());
            assertTrue(cancelled.await(1, TimeUnit.SECONDS));
        }

        private static void awaitCancellation(CountDownLatch cancelled) throws InterruptedException {
            Context.current().addListener(_ -> cancelled.countDown(), Runnable::run);
            cancelled.await(5, TimeUnit.SECONDS);
        }

        private HubManager hubWithProjects(ProjectManager... projects) {
            WorkspaceInfo workspace = workspace("ws-1", "default");
            ProjectsManager projectsManager = mock(ProjectsManager.class);
            when(projectsManager.findAllOrThrow()).thenReturn(List.of(projects));

            WorkspaceManager workspaceManager = mock(WorkspaceManager.class);
            when(workspaceManager.projectsManager()).thenReturn(projectsManager);

            HubManager hub = mock(HubManager.class);
            when(hub.info()).thenReturn(hubInfo("h-1", "production"));
            when(hub.infoOrThrow()).thenReturn(new DiscoveryClient.PublicApiInfo("1.0", 1));
            when(hub.workspacesOrThrow()).thenReturn(List.of(workspace));
            when(hub.workspace(workspace)).thenReturn(workspaceManager);
            return hub;
        }

        private ProjectManager project(String id, String name, RepositoryManager repository) {
            ProjectManager project = mock(ProjectManager.class);
            when(project.info()).thenReturn(projectInfo(id, name));
            when(project.repositoryManager()).thenReturn(repository);
            return project;
        }

        @Test
        void reportsEveryHubWhenNoneAnswered() {
            HubManager production = unreachableHub("h-1", "production");
            HubManager staging = unreachableHub("h-2", "staging");
            when(hubsManager.findAll()).thenReturn(List.of(production, staging));

            HubSessionScan.Result result = scan.scan(HubScanFilter.ALL, 0);

            assertEquals(2, result.failures().size());
        }
    }

    @Nested
    class Filtering {

        @Test
        void pushesTheWindowDownToTheHub() {
            RepositoryManager repo = repositoryWith(session("s-1", NOW));
            HubManager production = reachableHub("h-1", "production", "checkout", repo);
            when(hubsManager.findAll()).thenReturn(List.of(production));

            RecordingSessionFilter lastHour =
                    RecordingSessionFilter.activeWithinLast(Duration.ofHours(1), NOW);
            scan.scan(HubScanFilter.ALL.withSessions(lastHour), 0);

            ArgumentCaptor<RecordingSessionFilter> captor =
                    ArgumentCaptor.forClass(RecordingSessionFilter.class);
            verify(repo).listRecordingSessions(eq(true), captor.capture());
            assertEquals(NOW.minus(Duration.ofHours(1)), captor.getValue().activeFrom());
        }

        @Test
        void filtersHubsByANameFragment() {
            HubManager production = reachableHub("h-1", "production", "checkout",
                    repositoryWith(session("s-1", NOW)));
            HubManager staging = reachableHub("h-2", "staging", "search",
                    repositoryWith(session("s-2", NOW)));
            when(hubsManager.findAll()).thenReturn(List.of(production, staging));

            HubSessionScan.Result result =
                    scan.scan(new HubScanFilter("prod", null, null, RecordingSessionFilter.ALL), 0);

            assertEquals(1, result.rows().size());
            assertEquals("production", result.rows().getFirst().hubName());
        }

        @Test
        void filtersHubsByExactId() {
            HubManager production = reachableHub("h-1", "production", "checkout",
                    repositoryWith(session("s-1", NOW)));
            HubManager staging = reachableHub("h-2", "staging", "search",
                    repositoryWith(session("s-2", NOW)));
            when(hubsManager.findAll()).thenReturn(List.of(production, staging));

            HubSessionScan.Result result =
                    scan.scan(new HubScanFilter("h-2", null, null, RecordingSessionFilter.ALL), 0);

            assertEquals("staging", result.rows().getFirst().hubName());
        }

        @Test
        void neverTouchesAHubItFilteredOut() {
            HubManager filteredOut = mock(HubManager.class);
            when(filteredOut.info()).thenReturn(hubInfo("h-2", "staging"));
            HubManager production = reachableHub("h-1", "production", "checkout",
                    repositoryWith(session("s-1", NOW)));
            when(hubsManager.findAll()).thenReturn(List.of(production, filteredOut));

            scan.scan(new HubScanFilter("production", null, null, RecordingSessionFilter.ALL), 0);

            verify(filteredOut, never()).infoOrThrow();
        }

        @Test
        void filtersProjectsByName() {
            HubManager production = reachableHub("h-1", "production", "checkout",
                    repositoryWith(session("s-1", NOW)));
            when(hubsManager.findAll()).thenReturn(List.of(production));

            HubSessionScan.Result result =
                    scan.scan(new HubScanFilter(null, null, "search", RecordingSessionFilter.ALL), 0);

            assertTrue(result.rows().isEmpty());
        }

        @Test
        void reportsAWorkspaceWhoseManagerCannotBeCreated() {
            HubManager hub = mock(HubManager.class);
            when(hub.info()).thenReturn(hubInfo("h-1", "production"));
            when(hub.infoOrThrow()).thenReturn(new DiscoveryClient.PublicApiInfo("1.0", 1));
            WorkspaceInfo gone = workspace("ws-gone", "default");
            when(hub.workspacesOrThrow()).thenReturn(List.of(gone));
            when(hub.workspace(gone)).thenThrow(new IllegalStateException("workspace disappeared"));
            when(hubsManager.findAll()).thenReturn(List.of(hub));

            HubSessionScan.Result result = scan.scan(HubScanFilter.ALL, 0);

            assertTrue(result.rows().isEmpty());
            assertTrue(result.failures().getFirst().reason().contains("workspace disappeared"));
        }
    }
}
