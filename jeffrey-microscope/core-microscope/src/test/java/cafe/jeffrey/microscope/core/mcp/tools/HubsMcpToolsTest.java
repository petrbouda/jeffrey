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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.hub.client.DiscoveryClient;
import cafe.jeffrey.hub.client.manager.RepositoryManager;
import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.microscope.core.manager.project.ProjectsManager;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.manager.server.HubManager;
import cafe.jeffrey.microscope.core.manager.server.HubsManager;
import cafe.jeffrey.microscope.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.microscope.core.mcp.tools.hubs.HubSessionRef;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.microscope.persistence.api.RecordingTag;
import cafe.jeffrey.recordings.core.RecordingsDownloadManager;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.Recording;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.shared.common.model.hub.HubAddress;
import cafe.jeffrey.shared.common.model.hub.HubInfo;
import cafe.jeffrey.shared.common.model.hub.HubSource;
import cafe.jeffrey.shared.common.model.repository.RecordingSession;
import cafe.jeffrey.shared.common.model.repository.RecordingSessionFilter;
import cafe.jeffrey.shared.common.model.repository.RecordingStatus;
import cafe.jeffrey.shared.common.model.repository.RepositoryFile;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceStatus;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HubsMcpToolsTest {

    private static final Instant NOW = Instant.parse("2026-03-01T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final String HUB_ID = "cfg-production";
    private static final String WORKSPACE_ID = "ws-1";
    private static final String PROJECT_ID = "proj-1";
    private static final String SESSION_ID = "session-1";
    private static final HubSessionRef REF =
            new HubSessionRef(HUB_ID, WORKSPACE_ID, PROJECT_ID, SESSION_ID);

    private final HubsManager hubsManager = mock(HubsManager.class);
    private final ProjectManagerResolver resolver = mock(ProjectManagerResolver.class);
    private final RecordingsManager recordingsManager = mock(RecordingsManager.class);

    private final HubsMcpTools tools =
            new HubsMcpTools(hubsManager, resolver, recordingsManager, CLOCK);

    private static HubInfo hubInfo(String id, String name) {
        return new HubInfo(id, name, new HubAddress("hub.example.com", 443, false), NOW, HubSource.CONFIG);
    }

    private static RepositoryFile file(String id, String name, SupportedRecordingFile type) {
        return new RepositoryFile(id, name, NOW, 1024L, type, RecordingStatus.FINISHED, null);
    }

    private static RecordingSession session(String id, Instant createdAt, RepositoryFile... files) {
        return new RecordingSession(id, id, "inst-1", createdAt, createdAt.plusSeconds(600),
                RecordingStatus.FINISHED, null, null, List.of(files), false);
    }

    private static RecordingSession jfrSession(String id, Instant createdAt) {
        return session(id, createdAt, file("f-1", "recording.jfr", SupportedRecordingFile.JFR));
    }

    private RepositoryManager repositoryWith(RecordingSession... sessions) {
        RepositoryManager repo = mock(RepositoryManager.class);
        when(repo.listRecordingSessions(anyBoolean(), any())).thenReturn(List.of(sessions));
        return repo;
    }

    /**
     * A hub that answers its probe, with one workspace holding one project.
     */
    private HubManager reachableHub(String hubId, String hubName, String projectName, RepositoryManager repo) {
        HubManager hub = mock(HubManager.class);
        when(hub.info()).thenReturn(hubInfo(hubId, hubName));
        when(hub.tryInfo()).thenReturn(Optional.of(new DiscoveryClient.PublicApiInfo("2.1.0", 1)));
        when(hub.workspaces()).thenReturn(List.of(new WorkspaceInfo(
                WORKSPACE_ID, "ref", "repo", "default", null, null, NOW, WorkspaceStatus.AVAILABLE, 1)));

        ProjectManager project = mock(ProjectManager.class);
        when(project.info()).thenReturn(new ProjectInfo(
                PROJECT_ID, "origin", projectName, projectName, "ns", WORKSPACE_ID, NOW, NOW, Map.of(), null));
        when(project.repositoryManager()).thenReturn(repo);

        ProjectsManager projects = mock(ProjectsManager.class);
        when(projects.findAll()).thenReturn(List.of(project));

        WorkspaceManager workspaceManager = mock(WorkspaceManager.class);
        when(workspaceManager.projectsManager()).thenReturn(projects);
        when(hub.workspace(WORKSPACE_ID)).thenReturn(Optional.of(workspaceManager));
        return hub;
    }

    private void noLocalRecordings() {
        when(recordingsManager.listRecordings()).thenReturn(List.of());
    }

    private void localRecording(String recordingId, String profileId, HubSessionRef ref) {
        when(recordingsManager.listRecordings()).thenReturn(List.of(new Recording(
                recordingId, recordingId, null, null, RecordingEventSource.JDK, NOW, NOW, NOW,
                profileId != null, profileId, profileId, List.of())));
        when(recordingsManager.tagsForRecordings(any())).thenReturn(Map.of(recordingId, List.of(
                new RecordingTag("origin.hubId", ref.hubId()),
                new RecordingTag("origin.workspaceId", ref.workspaceId()),
                new RecordingTag("origin.projectId", ref.projectId()),
                new RecordingTag("origin.recordingId", ref.sessionId()))));
    }

    @Nested
    class ListHubs {

        @Test
        void namesEveryConnectedHubWithItsAddress() {
            HubManager production = reachableHub(HUB_ID, "production", "checkout", repositoryWith());
            when(hubsManager.findAll()).thenReturn(List.of(production));

            String result = tools.list();

            assertTrue(result.contains("production"), result);
            assertTrue(result.contains("hub.example.com:443"), result);
            assertTrue(result.contains("ok"), result);
        }

        @Test
        void marksAHubThatDidNotAnswer() {
            HubManager down = mock(HubManager.class);
            when(down.info()).thenReturn(hubInfo(HUB_ID, "production"));
            when(down.tryInfo()).thenReturn(Optional.empty());
            when(hubsManager.findAll()).thenReturn(List.of(down));

            assertTrue(tools.list().contains("unreachable"));
        }

        @Test
        void saysSoWhenNoHubIsConnectedAtAll() {
            when(hubsManager.findAll()).thenReturn(List.of());

            String result = tools.list();

            assertTrue(result.contains("No Jeffrey Hub is connected"), result);
            assertTrue(result.contains("recordings_analyzeFile"), result);
        }

        @Test
        void distinguishesAConfiguredHubFromOneAddedByHand() {
            HubManager configured = reachableHub(HUB_ID, "production", "checkout", repositoryWith());
            when(hubsManager.findAll()).thenReturn(List.of(configured));

            assertTrue(tools.list().contains("config"));
        }
    }

    @Nested
    class Sessions {

        @Test
        void rendersOneRowPerSessionCarryingItsRef() {
            HubManager production = reachableHub(
                    HUB_ID, "production", "checkout", repositoryWith(jfrSession(SESSION_ID, NOW)));
            when(hubsManager.findAll()).thenReturn(List.of(production));
            noLocalRecordings();

            String result = tools.sessions(null, null, null, null, null, null);

            assertTrue(result.contains("production"), result);
            assertTrue(result.contains("checkout"), result);
            assertTrue(result.contains(REF.encode()), result);
        }

        @Test
        void marksASessionAlreadyDownloadedButNotAnalysed() {
            HubManager production = reachableHub(
                    HUB_ID, "production", "checkout", repositoryWith(jfrSession(SESSION_ID, NOW)));
            when(hubsManager.findAll()).thenReturn(List.of(production));
            localRecording("rec-1", null, REF);

            String result = tools.sessions(null, null, null, null, null, null);

            assertTrue(result.contains("recording:rec-1"), result);
        }

        @Test
        void showsTheProfileForASessionAlreadyAnalysed() {
            HubManager production = reachableHub(
                    HUB_ID, "production", "checkout", repositoryWith(jfrSession(SESSION_ID, NOW)));
            when(hubsManager.findAll()).thenReturn(List.of(production));
            localRecording("rec-1", "profile-1", REF);

            String result = tools.sessions(null, null, null, null, null, null);

            assertTrue(result.contains("profile:profile-1"), result);
        }

        @Test
        void reportsAnUnreachableHubUnderTheTable() {
            HubManager down = mock(HubManager.class);
            when(down.info()).thenReturn(hubInfo("cfg-down", "production"));
            when(down.tryInfo()).thenReturn(Optional.empty());
            HubManager up = reachableHub(
                    "cfg-up", "staging", "search", repositoryWith(jfrSession(SESSION_ID, NOW)));
            when(hubsManager.findAll()).thenReturn(List.of(down, up));
            noLocalRecordings();

            String result = tools.sessions(null, null, null, null, null, null);

            assertTrue(result.contains("Not listed"), result);
            assertTrue(result.contains("production"), result);
        }

        @Test
        void reportsAnUnreachableHubEvenWhenNothingMatched() {
            // The answer that would otherwise mislead: "no sessions" while production is simply down.
            HubManager down = mock(HubManager.class);
            when(down.info()).thenReturn(hubInfo("cfg-down", "production"));
            when(down.tryInfo()).thenReturn(Optional.empty());
            when(hubsManager.findAll()).thenReturn(List.of(down));
            noLocalRecordings();

            String result = tools.sessions(null, null, null, null, null, null);

            assertTrue(result.contains("Not listed"), result);
            assertFalse(result.contains("No recording sessions matched"), result);
        }

        @Test
        void translatesTheLastHourIntoAWindowOnTheHub() {
            RepositoryManager repo = repositoryWith(jfrSession(SESSION_ID, NOW));
            HubManager production = reachableHub(HUB_ID, "production", "checkout", repo);
            when(hubsManager.findAll()).thenReturn(List.of(production));
            noLocalRecordings();

            tools.sessions(null, null, null, 60, null, null);

            ArgumentCaptor<RecordingSessionFilter> captor =
                    ArgumentCaptor.forClass(RecordingSessionFilter.class);
            verify(repo).listRecordingSessions(eq(true), captor.capture());
            assertEquals(NOW.minus(Duration.ofHours(1)), captor.getValue().activeFrom());
        }

        @Test
        void pushesTheStatusDownToTheHub() {
            RepositoryManager repo = repositoryWith(jfrSession(SESSION_ID, NOW));
            HubManager production = reachableHub(HUB_ID, "production", "checkout", repo);
            when(hubsManager.findAll()).thenReturn(List.of(production));
            noLocalRecordings();

            tools.sessions(null, null, null, null, "active", null);

            ArgumentCaptor<RecordingSessionFilter> captor =
                    ArgumentCaptor.forClass(RecordingSessionFilter.class);
            verify(repo).listRecordingSessions(eq(true), captor.capture());
            assertEquals(RecordingStatus.ACTIVE, captor.getValue().status());
        }

        @Test
        void rejectsAStatusThatIsNotOne() {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> tools.sessions(null, null, null, null, "RUNNING", null));

            assertTrue(e.getMessage().contains("ACTIVE"), e.getMessage());
        }

        @Test
        void rejectsAWindowThatIsNotAWindow() {
            assertThrows(IllegalArgumentException.class,
                    () -> tools.sessions(null, null, null, 0, null, null));
        }

        @Test
        void boundsTheLimitItPushesDown() {
            RepositoryManager repo = repositoryWith(jfrSession(SESSION_ID, NOW));
            HubManager production = reachableHub(HUB_ID, "production", "checkout", repo);
            when(hubsManager.findAll()).thenReturn(List.of(production));
            noLocalRecordings();

            tools.sessions(null, null, null, null, null, 10_000);

            ArgumentCaptor<RecordingSessionFilter> captor =
                    ArgumentCaptor.forClass(RecordingSessionFilter.class);
            verify(repo).listRecordingSessions(eq(true), captor.capture());
            assertEquals(500, captor.getValue().limit());
        }

        @Test
        void keepsAPipeInAProjectNameOffTheColumnBoundaries() {
            HubManager production = reachableHub(
                    HUB_ID, "production", "check|out", repositoryWith(jfrSession(SESSION_ID, NOW)));
            when(hubsManager.findAll()).thenReturn(List.of(production));
            noLocalRecordings();

            assertTrue(tools.sessions(null, null, null, null, null, null).contains("check/out"));
        }

        @Test
        void saysHowToWidenAWindowThatMatchedNothing() {
            HubManager production = reachableHub(HUB_ID, "production", "checkout", repositoryWith());
            when(hubsManager.findAll()).thenReturn(List.of(production));

            String result = tools.sessions(null, null, null, 60, null, null);

            assertTrue(result.contains("withinLastMinutes"), result);
            assertTrue(result.contains("60"), result);
        }

        @Test
        void pointsAtTheHubsWhenNothingMatchedAndNoWindowWasGiven() {
            HubManager production = reachableHub(HUB_ID, "production", "checkout", repositoryWith());
            when(hubsManager.findAll()).thenReturn(List.of(production));

            assertTrue(tools.sessions(null, null, null, null, null, null).contains("hubs_list"));
        }
    }

    @Nested
    class Download {

        private ProjectManager projectWith(RecordingSession session, RecordingsDownloadManager downloads) {
            RepositoryManager repo = mock(RepositoryManager.class);
            when(repo.recordingSession(SESSION_ID)).thenReturn(session);

            ProjectManager project = mock(ProjectManager.class);
            when(project.repositoryManager()).thenReturn(repo);
            when(project.info()).thenReturn(new ProjectInfo(
                    PROJECT_ID, "origin", "checkout", "checkout", "ns", WORKSPACE_ID, NOW, NOW, Map.of(), null));
            if (downloads != null) {
                when(project.recordingsDownloadManager()).thenReturn(downloads);
            }
            return project;
        }

        private void resolvesTo(ProjectManager project) {
            HubManager hub = mock(HubManager.class);
            when(hub.info()).thenReturn(hubInfo(HUB_ID, "production"));
            when(resolver.resolveServer(HUB_ID)).thenReturn(hub);
            when(resolver.resolve(HUB_ID, WORKSPACE_ID, PROJECT_ID)).thenReturn(
                    new ProjectManagerResolver.ProjectContext(
                            mock(WorkspaceManager.class), mock(ProjectsManager.class), project));
        }

        @Test
        void downloadsTheSessionTheRefNamesAndReturnsTheRecordingId() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.mergeAndDownloadSession(SESSION_ID)).thenReturn("rec-new");
            resolvesTo(projectWith(jfrSession(SESSION_ID, NOW), downloads));
            noLocalRecordings();

            String result = tools.download(REF.encode());

            assertTrue(result.contains("\"recordingId\":\"rec-new\""), result);
            verify(downloads).mergeAndDownloadSession(SESSION_ID);
        }

        @Test
        void pointsAtRecordingsAnalyzeRecordingAsTheNextStep() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.mergeAndDownloadSession(SESSION_ID)).thenReturn("rec-new");
            resolvesTo(projectWith(jfrSession(SESSION_ID, NOW), downloads));
            noLocalRecordings();

            assertTrue(tools.download(REF.encode()).contains("recordings_analyzeRecording"));
        }

        @Test
        void countsTheRecordingAndArtifactFilesItBrought() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.mergeAndDownloadSession(SESSION_ID)).thenReturn("rec-new");
            RecordingSession withHeapDump = session(SESSION_ID, NOW,
                    file("f-1", "recording.jfr", SupportedRecordingFile.JFR),
                    file("f-2", "heap.hprof", SupportedRecordingFile.HEAP_DUMP));
            resolvesTo(projectWith(withHeapDump, downloads));
            noLocalRecordings();

            String result = tools.download(REF.encode());

            assertTrue(result.contains("\"recordingFiles\":1"), result);
            assertTrue(result.contains("\"artifactFiles\":1"), result);
        }

        @Test
        void returnsTheExistingRecordingRatherThanFetchingItTwice() {
            localRecording("rec-existing", null, REF);

            String result = tools.download(REF.encode());

            assertTrue(result.contains("rec-existing"), result);
            verifyNoInteractions(resolver);
        }

        @Test
        void pointsStraightAtAnalysisWhenTheSessionIsAlreadyAProfile() {
            localRecording("rec-existing", "profile-1", REF);

            String result = tools.download(REF.encode());

            assertTrue(result.contains("profile-1"), result);
            verifyNoInteractions(resolver);
        }

        @Test
        void rejectsAMalformedRefWithoutOpeningAHubConnection() {
            assertThrows(IllegalArgumentException.class, () -> tools.download("not-a-ref"));

            verifyNoInteractions(resolver);
            verifyNoInteractions(recordingsManager);
        }

        @Test
        void explainsARefWhoseHubIsGone() {
            noLocalRecordings();
            when(resolver.resolveServer(HUB_ID)).thenThrow(Exceptions.invalidRequest("Hub not found"));

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class, () -> tools.download(REF.encode()));

            assertTrue(e.getMessage().contains("hubs_sessions"), e.getMessage());
        }

        @Test
        void explainsASessionTheHubNoLongerHas() {
            noLocalRecordings();
            RepositoryManager repo = mock(RepositoryManager.class);
            when(repo.recordingSession(SESSION_ID))
                    .thenThrow(Exceptions.invalidRequest("Session not found"));
            ProjectManager project = mock(ProjectManager.class);
            when(project.repositoryManager()).thenReturn(repo);
            resolvesTo(project);

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class, () -> tools.download(REF.encode()));

            assertTrue(e.getMessage().contains("retention"), e.getMessage());
        }

        @Test
        void refusesASessionWithNothingFinishedToDownload() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            RecordingSession logsOnly = session(SESSION_ID, NOW,
                    file("f-1", "gc.log", SupportedRecordingFile.JVM_LOG));
            resolvesTo(projectWith(logsOnly, downloads));
            noLocalRecordings();

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class, () -> tools.download(REF.encode()));

            assertTrue(e.getMessage().contains("no finished recording file"), e.getMessage());
            verify(downloads, never()).mergeAndDownloadSession(any());
        }
    }
}
