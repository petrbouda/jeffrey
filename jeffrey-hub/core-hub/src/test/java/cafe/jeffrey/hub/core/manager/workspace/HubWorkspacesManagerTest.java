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

package cafe.jeffrey.hub.core.manager.workspace;

import cafe.jeffrey.hub.core.manager.project.HubProjectManager;
import cafe.jeffrey.hub.core.manager.project.HubProjectsManager;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.volume.TestVolume;
import cafe.jeffrey.hub.model.workspace.WorkspaceInfo;
import cafe.jeffrey.shared.volume.FilesystemVolume;
import cafe.jeffrey.shared.volume.layout.WorkspaceMarker;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Workspaces are directories under the workspaces root, named by their id, with the hub's
 * {@code .workspace-info.json} for a name.
 */
class HubWorkspacesManagerTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @TempDir
    Path root;

    private HubWorkspacesManager manager(boolean autoCreate) {
        Clock clock = FIXED_CLOCK;
        FilesystemVolume volume = new FilesystemVolume(root);
        ProjectManager.Factory projectFactory =
                project -> new HubProjectManager(project, volume);
        return new HubWorkspacesManager(volume, clock, autoCreate,
                workspace -> new HubWorkspaceManager(workspace, ws -> new HubProjectsManager(ws, projectFactory)));
    }

    private HubWorkspacesManager manager() {
        return manager(false);
    }

    private static List<String> ids(List<WorkspaceManager> workspaces) {
        return workspaces.stream().map(workspace -> workspace.info().id()).toList();
    }

    @Nested
    class Create {

        @Test
        void writesTheDirectoryAndItsMarker() {
            WorkspaceInfo created = manager().create(new WorkspacesManager.CreateWorkspaceRequest("prod-ws", "Production"));

            assertEquals(new WorkspaceInfo("prod-ws", "Production", NOW, 0), created);
            assertTrue(Files.isRegularFile(root.resolve("prod-ws").resolve(WorkspaceMarker.FILE_NAME)));
        }

        /** The provisioner may write into a reference id before anyone creates it; its projects stay. */
        @Test
        void adoptsADirectoryTheProvisionerAlreadyWroteInto() {
            TestVolume.project(root.resolve("prod-ws"), "p-1", "orders", NOW);

            WorkspaceInfo created = manager().create(new WorkspacesManager.CreateWorkspaceRequest("prod-ws", "Production"));

            assertEquals(1, created.projectCount());
        }

        /** With auto-create on, the directory is listed under its id as its name before it is created. */
        @Test
        void adoptsAnAutoCreatedDirectoryUnderTheNameItWasListedWith() throws IOException {
            Files.createDirectories(root.resolve("prod-ws"));
            HubWorkspacesManager manager = manager(true);
            assertFalse(manager.isDescribed("prod-ws"));

            WorkspaceInfo created = manager.create(new WorkspacesManager.CreateWorkspaceRequest("prod-ws", "prod-ws"));

            assertEquals("prod-ws", created.name());
            assertTrue(manager.isDescribed("prod-ws"));
        }

        @Test
        void blankReferenceId_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () ->
                    manager().create(new WorkspacesManager.CreateWorkspaceRequest("  ", "Name")));
        }

        @Test
        void invalidFormatReferenceId_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () ->
                    manager().create(new WorkspacesManager.CreateWorkspaceRequest("../escape", "Name")));
        }

        @Test
        void blankName_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () ->
                    manager().create(new WorkspacesManager.CreateWorkspaceRequest("prod-ws", " ")));
        }

        @Test
        void duplicateReferenceId_throwsAlreadyExists() {
            TestVolume.workspace(root, "prod-ws", "Production", NOW);

            assertThrows(WorkspaceAlreadyExistsException.class, () ->
                    manager().create(new WorkspacesManager.CreateWorkspaceRequest("prod-ws", "Other")));
        }

        @Test
        void duplicateName_throwsAlreadyExists() {
            TestVolume.workspace(root, "prod-ws", "Production", NOW);

            assertThrows(WorkspaceAlreadyExistsException.class, () ->
                    manager().create(new WorkspacesManager.CreateWorkspaceRequest("other-ws", "Production")));
        }
    }

    @Nested
    class Listing {

        @Test
        void listsEveryWorkspaceWithItsMarkerAndSkipsDotDirectories() throws IOException {
            TestVolume.workspace(root, "prod-ws", "Production", NOW);
            TestVolume.workspace(root, "staging-ws", "Staging", NOW);
            Files.createDirectories(root.resolve(".pending"));

            assertEquals(List.of("prod-ws", "staging-ws"), ids(manager().findAll()));
        }

        @Test
        void aDirectoryWithoutTheMarkerIsLeftOutUnlessAutoCreateIsOn() throws IOException {
            Files.createDirectories(root.resolve("announced-ws"));

            assertTrue(manager(false).findAll().isEmpty());

            WorkspaceInfo info = manager(true).findAll().getFirst().info();
            assertEquals("announced-ws", info.name(), "named by its id until someone creates it");
        }

        @Test
        void countsTheProjectsInIt() {
            Path workspace = TestVolume.workspace(root, "prod-ws", "Production", NOW);
            TestVolume.project(workspace, "p-1", "orders", NOW);
            TestVolume.project(workspace, "p-2", "payments", NOW);

            assertEquals(2, manager().findAll().getFirst().info().projectCount());
        }
    }
}
