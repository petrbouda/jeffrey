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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Workspaces are directories under the workspaces root, named by their reference id; nothing in them is read. */
class HubWorkspacesManagerTest {

    @TempDir
    Path root;

    private HubWorkspacesManager manager() {
        FilesystemVolume volume = new FilesystemVolume(root);
        ProjectManager.Factory projectFactory =
                project -> new HubProjectManager(project, volume);
        return new HubWorkspacesManager(volume,
                workspace -> new HubWorkspaceManager(workspace, ws -> new HubProjectsManager(ws, projectFactory)));
    }

    private static List<String> ids(List<WorkspaceManager> workspaces) {
        return workspaces.stream().map(workspace -> workspace.info().id()).toList();
    }

    @Nested
    class Create {

        @Test
        void writesItsDirectoryAndNothingInIt() throws IOException {
            WorkspaceInfo created = manager().create("prod-ws");

            assertEquals("prod-ws", created.id());
            assertEquals(0, created.projectCount());
            try (var entries = Files.list(root.resolve("prod-ws"))) {
                assertEquals(0, entries.count());
            }
        }

        @Test
        void blankReferenceId_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> manager().create("  "));
        }

        @Test
        void invalidFormatReferenceId_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> manager().create("../escape"));
        }

        /** The provisioner may have written into the reference id first: it is a workspace already. */
        @Test
        void aDirectoryAlreadyThere_throwsAlreadyExists() {
            TestVolume.project(root.resolve("prod-ws"), "orders");

            assertThrows(WorkspaceAlreadyExistsException.class, () -> manager().create("prod-ws"));
            assertTrue(manager().exists("prod-ws"));
            assertFalse(manager().exists("other-ws"));
        }
    }

    @Nested
    class Listing {

        @Test
        void listsEveryDirectoryNamedByAReferenceIdAndSkipsDotDirectories() throws IOException {
            TestVolume.workspace(root, "prod-ws");
            TestVolume.workspace(root, "staging-ws");
            Files.createDirectories(root.resolve(".pending"));

            assertEquals(List.of("prod-ws", "staging-ws"), ids(manager().findAll()));
        }

        @Test
        void countsTheProjectsInIt() {
            Path workspace = TestVolume.workspace(root, "prod-ws");
            TestVolume.project(workspace, "orders");
            TestVolume.project(workspace, "payments");

            assertEquals(2, manager().findAll().getFirst().info().projectCount());
        }
    }
}
