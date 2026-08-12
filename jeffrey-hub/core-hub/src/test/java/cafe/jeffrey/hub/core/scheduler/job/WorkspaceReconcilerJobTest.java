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

package cafe.jeffrey.hub.core.scheduler.job;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cafe.jeffrey.hub.core.HubJeffreyDirs;
import cafe.jeffrey.hub.core.configuration.properties.DefaultWorkspaceProperties;
import cafe.jeffrey.hub.core.manager.project.ProjectsManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.core.scheduler.JobContext;
import cafe.jeffrey.hub.core.workspace.reconcile.WorkspaceReconciler;
import cafe.jeffrey.shared.common.JeffreyLayout;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Discovery is driven entirely by each workspace's pending index: the job never walks project
 * directories looking for work, it only reconciles what the provisioner announced.
 */
@ExtendWith(MockitoExtension.class)
class WorkspaceReconcilerJobTest {

    private static final String DEFAULT_REF_ID = "default-workspace";
    private static final String KNOWN_REF_ID = "known-ws";
    private static final String UNKNOWN_REF_ID = "unknown-ws";
    private static final String PROJECT_NAME = "some-project";

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-02-20T15:30:45.123Z"), ZoneOffset.UTC);

    private static final WorkspaceInfo WORKSPACE_INFO = new WorkspaceInfo(
            "ws-internal-1", KNOWN_REF_ID, null, "Known", null, null,
            Instant.parse("2025-01-01T10:00:00Z"), null, 0);

    @Mock
    WorkspacesManager workspacesManager;

    @Mock
    WorkspaceManager workspaceManager;

    @Mock
    ProjectsManager projectsManager;

    @Mock
    WorkspaceReconciler reconciler;

    @TempDir
    Path tempDir;

    private Path workspacesRoot;

    @BeforeEach
    void setUp() {
        workspacesRoot = createDir(tempDir.resolve("workspaces"));
    }

    private WorkspaceReconcilerJob job(boolean autoCreate) {
        return new WorkspaceReconcilerJob(
                workspacesManager,
                reconciler,
                new HubJeffreyDirs(tempDir),
                new DefaultWorkspaceProperties(DEFAULT_REF_ID, DEFAULT_REF_ID),
                autoCreate,
                FIXED_CLOCK,
                Duration.ofSeconds(2));
    }

    private Path workspaceDir(String refId) {
        return createDir(workspacesRoot.resolve(refId));
    }

    /** Mirrors what the provisioner writes: an entry naming a path relative to the workspace. */
    private static void announce(Path workspaceDir, String entryId, String relativePath) {
        Path pendingDir = createDir(workspaceDir.resolve(JeffreyLayout.PENDING_DIR));
        write(pendingDir.resolve("20260220153045123_" + entryId), relativePath);
    }

    private static List<Path> pendingEntries(Path workspaceDir) {
        Path pendingDir = workspaceDir.resolve(JeffreyLayout.PENDING_DIR);
        if (!Files.isDirectory(pendingDir)) {
            return List.of();
        }
        try (var files = Files.list(pendingDir)) {
            return files.sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void knownWorkspace() {
        when(workspacesManager.findByReferenceId(KNOWN_REF_ID)).thenReturn(Optional.of(workspaceManager));
        when(workspaceManager.projectsManager()).thenReturn(projectsManager);
    }

    private void reconcilerReports(int materialized, boolean declarationSeen) {
        when(reconciler.reconcileProjectDirectory(any(), any()))
                .thenReturn(new WorkspaceReconciler.Result(materialized, declarationSeen));
    }

    private static Path createDir(Path dir) {
        try {
            return Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Path file, String content) {
        try {
            Files.writeString(file, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Nested
    class IdleTicks {

        @Test
        void workspaceWithoutPendingEntries_doesNothingAtAll() {
            workspaceDir(KNOWN_REF_ID);

            job(false).execute(JobContext.EMPTY);

            verifyNoInteractions(workspacesManager, reconciler);
        }

        @Test
        void listingPendingEntries_doesNotCreateTheDirectory() {
            Path wsDir = workspaceDir(KNOWN_REF_ID);

            job(false).execute(JobContext.EMPTY);

            assertFalse(Files.exists(wsDir.resolve(JeffreyLayout.PENDING_DIR)));
        }
    }

    @Nested
    class AnnouncedWork {

        @Test
        void announcedProject_isReconciled() {
            Path wsDir = workspaceDir(KNOWN_REF_ID);
            announce(wsDir, "proj-001", PROJECT_NAME);
            knownWorkspace();
            reconcilerReports(1, true);

            job(false).execute(JobContext.EMPTY);

            verify(reconciler).reconcileProjectDirectory(projectsManager, wsDir.resolve(PROJECT_NAME));
        }

        @Test
        void deeperEntry_reconcilesItsProjectSubtree() {
            Path wsDir = workspaceDir(KNOWN_REF_ID);
            announce(wsDir, "session-001", PROJECT_NAME + "/inst-001/session-001");
            knownWorkspace();
            reconcilerReports(1, true);

            job(false).execute(JobContext.EMPTY);

            verify(reconciler).reconcileProjectDirectory(projectsManager, wsDir.resolve(PROJECT_NAME));
        }

        @Test
        void severalEntriesForOneProject_reconcileItOnce() {
            Path wsDir = workspaceDir(KNOWN_REF_ID);
            announce(wsDir, "proj-001", PROJECT_NAME);
            announce(wsDir, "inst-001", PROJECT_NAME + "/inst-001");
            announce(wsDir, "session-001", PROJECT_NAME + "/inst-001/session-001");
            knownWorkspace();
            reconcilerReports(3, true);

            job(false).execute(JobContext.EMPTY);

            verify(reconciler, times(1)).reconcileProjectDirectory(any(), any());
        }

        @Test
        void entriesOfDistinctProjects_areReconciledSeparately() {
            Path wsDir = workspaceDir(KNOWN_REF_ID);
            announce(wsDir, "proj-001", "project-alpha");
            announce(wsDir, "proj-002", "project-beta");
            knownWorkspace();
            reconcilerReports(1, true);

            job(false).execute(JobContext.EMPTY);

            verify(reconciler).reconcileProjectDirectory(projectsManager, wsDir.resolve("project-alpha"));
            verify(reconciler).reconcileProjectDirectory(projectsManager, wsDir.resolve("project-beta"));
        }
    }

    @Nested
    class EntryLifecycle {

        @Test
        void reconciledEntries_areRemoved() {
            Path wsDir = workspaceDir(KNOWN_REF_ID);
            announce(wsDir, "proj-001", PROJECT_NAME);
            announce(wsDir, "inst-001", PROJECT_NAME + "/inst-001");
            knownWorkspace();
            reconcilerReports(2, true);

            job(false).execute(JobContext.EMPTY);

            assertTrue(pendingEntries(wsDir).isEmpty());
        }

        /**
         * The provisioner writes the marker before the hint, but a tick can still land between
         * the two. Consuming such a hint would lose the declaration for good.
         */
        @Test
        void entryWhoseDeclarationIsNotReadableYet_isKept() {
            Path wsDir = workspaceDir(KNOWN_REF_ID);
            announce(wsDir, "proj-001", PROJECT_NAME);
            knownWorkspace();
            reconcilerReports(0, false);

            job(false).execute(JobContext.EMPTY);

            assertEquals(1, pendingEntries(wsDir).size());
        }

        @Test
        void entryPointingAtARemovedDirectory_doesNotFailTheTick() {
            Path wsDir = workspaceDir(KNOWN_REF_ID);
            announce(wsDir, "proj-001", "never-existed");
            knownWorkspace();
            reconcilerReports(0, false);

            assertDoesNotThrow(() -> job(false).execute(JobContext.EMPTY));
        }

        @Test
        void entryNamingNothing_isIgnored() {
            Path wsDir = workspaceDir(KNOWN_REF_ID);
            announce(wsDir, "broken", "   ");

            job(false).execute(JobContext.EMPTY);

            verifyNoInteractions(reconciler);
        }
    }

    @Nested
    class WorkspaceResolution {

        @Test
        void unknownWorkspace_isSkipped_whenAutoCreateDisabled() {
            Path wsDir = workspaceDir(UNKNOWN_REF_ID);
            announce(wsDir, "proj-001", PROJECT_NAME);
            when(workspacesManager.findByReferenceId(UNKNOWN_REF_ID)).thenReturn(Optional.empty());

            job(false).execute(JobContext.EMPTY);

            verify(workspacesManager, never()).create(any());
            verifyNoInteractions(reconciler);
            assertEquals(1, pendingEntries(wsDir).size(), "Entries survive so a later tick can retry");
        }

        @Test
        void announcedUnknownWorkspace_isAutoCreated_whenEnabled() {
            Path wsDir = workspaceDir(UNKNOWN_REF_ID);
            announce(wsDir, "proj-001", PROJECT_NAME);
            when(workspacesManager.findByReferenceId(UNKNOWN_REF_ID))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(workspaceManager));
            when(workspacesManager.create(any())).thenReturn(WORKSPACE_INFO);
            when(workspaceManager.projectsManager()).thenReturn(projectsManager);
            reconcilerReports(1, true);

            job(true).execute(JobContext.EMPTY);

            verify(workspacesManager).create(any());
            verify(reconciler).reconcileProjectDirectory(projectsManager, wsDir.resolve(PROJECT_NAME));
        }

        @Test
        void strayDirectoryThatAnnouncesNothing_neverBecomesAWorkspace() {
            workspaceDir(UNKNOWN_REF_ID);

            job(true).execute(JobContext.EMPTY);

            verify(workspacesManager, never()).create(any());
            verifyNoInteractions(reconciler);
        }

        @Test
        void missingDefaultWorkspace_isNeverAutoCreated() {
            Path wsDir = workspaceDir(DEFAULT_REF_ID);
            announce(wsDir, "proj-001", PROJECT_NAME);
            when(workspacesManager.findByReferenceId(DEFAULT_REF_ID)).thenReturn(Optional.empty());

            job(true).execute(JobContext.EMPTY);

            verify(workspacesManager, never()).create(any());
            verifyNoInteractions(reconciler);
        }

        @Test
        void oneFailingWorkspace_doesNotStopTheOthers() {
            Path failing = workspaceDir("aaa-failing");
            Path healthy = workspaceDir(KNOWN_REF_ID);
            announce(failing, "proj-001", PROJECT_NAME);
            announce(healthy, "proj-002", PROJECT_NAME);

            when(workspacesManager.findByReferenceId("aaa-failing")).thenThrow(new RuntimeException("boom"));
            knownWorkspace();
            reconcilerReports(1, true);

            job(false).execute(JobContext.EMPTY);

            verify(reconciler).reconcileProjectDirectory(eq(projectsManager), eq(healthy.resolve(PROJECT_NAME)));
        }
    }
}
