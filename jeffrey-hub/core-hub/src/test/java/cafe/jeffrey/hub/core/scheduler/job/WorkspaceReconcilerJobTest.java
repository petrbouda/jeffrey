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
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkspaceReconcilerJobTest {

    private static final String DEFAULT_REF_ID = "default-workspace";
    private static final String KNOWN_REF_ID = "known-ws";
    private static final String UNKNOWN_REF_ID = "unknown-ws";

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
                Duration.ofSeconds(30));
    }

    private Path workspaceDir(String refId) {
        return createDir(workspacesRoot.resolve(refId));
    }

    private Path declareProjectIn(Path workspaceDir) {
        Path projectDir = createDir(workspaceDir.resolve("some-project"));
        write(projectDir.resolve(".project-info.json"), "{}");
        return projectDir;
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
    class WorkspaceResolution {

        @Test
        void knownWorkspace_isReconciled() {
            Path wsDir = workspaceDir(KNOWN_REF_ID);
            when(workspacesManager.findByReferenceId(KNOWN_REF_ID)).thenReturn(Optional.of(workspaceManager));
            when(workspaceManager.localInfo()).thenReturn(WORKSPACE_INFO);
            when(workspaceManager.projectsManager()).thenReturn(projectsManager);

            job(false).execute(JobContext.EMPTY);

            verify(reconciler).reconcile(WORKSPACE_INFO, projectsManager, wsDir);
        }

        @Test
        void unknownWorkspace_isSkipped_whenAutoCreateDisabled() {
            workspaceDir(UNKNOWN_REF_ID);
            when(workspacesManager.findByReferenceId(UNKNOWN_REF_ID)).thenReturn(Optional.empty());

            job(false).execute(JobContext.EMPTY);

            verify(workspacesManager, never()).create(any());
            verifyNoInteractions(reconciler);
        }

        @Test
        void unknownWorkspaceWithProjectDeclaration_isAutoCreated_whenEnabled() {
            Path wsDir = workspaceDir(UNKNOWN_REF_ID);
            declareProjectIn(wsDir);
            when(workspacesManager.findByReferenceId(UNKNOWN_REF_ID))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(workspaceManager));
            when(workspacesManager.create(any())).thenReturn(WORKSPACE_INFO);
            when(workspaceManager.localInfo()).thenReturn(WORKSPACE_INFO);
            when(workspaceManager.projectsManager()).thenReturn(projectsManager);

            job(true).execute(JobContext.EMPTY);

            verify(workspacesManager).create(any());
            verify(reconciler).reconcile(WORKSPACE_INFO, projectsManager, wsDir);
        }

        @Test
        void strayDirectoryWithoutProjectDeclaration_neverBecomesAWorkspace() {
            workspaceDir(UNKNOWN_REF_ID);
            when(workspacesManager.findByReferenceId(UNKNOWN_REF_ID)).thenReturn(Optional.empty());

            job(true).execute(JobContext.EMPTY);

            verify(workspacesManager, never()).create(any());
            verifyNoInteractions(reconciler);
        }

        @Test
        void missingDefaultWorkspace_isNeverAutoCreated() {
            Path wsDir = workspaceDir(DEFAULT_REF_ID);
            declareProjectIn(wsDir);
            when(workspacesManager.findByReferenceId(DEFAULT_REF_ID)).thenReturn(Optional.empty());

            job(true).execute(JobContext.EMPTY);

            verify(workspacesManager, never()).create(any());
            verifyNoInteractions(reconciler);
        }

        @Test
        void oneFailingWorkspace_doesNotStopTheOthers() {
            Path failing = workspaceDir("aaa-failing");
            Path healthy = workspaceDir(KNOWN_REF_ID);
            var failingManager = mock(WorkspaceManager.class);
            when(workspacesManager.findByReferenceId("aaa-failing")).thenReturn(Optional.of(failingManager));
            when(failingManager.localInfo()).thenThrow(new RuntimeException("boom"));
            when(workspacesManager.findByReferenceId(KNOWN_REF_ID)).thenReturn(Optional.of(workspaceManager));
            when(workspaceManager.localInfo()).thenReturn(WORKSPACE_INFO);
            when(workspaceManager.projectsManager()).thenReturn(projectsManager);

            job(false).execute(JobContext.EMPTY);

            verify(reconciler).reconcile(eq(WORKSPACE_INFO), eq(projectsManager), eq(healthy));
        }
    }
}
