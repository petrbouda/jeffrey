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
import cafe.jeffrey.shared.volume.FilesystemVolume;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Workspaces are directories under the workspaces root, named by their reference id; nothing in
 * them is read, and the hub creates none: the provisioner lays each one down.
 */
class HubWorkspacesManagerTest {

    @TempDir
    Path root;

    private HubWorkspacesManager manager() {
        return manager(root);
    }

    private static HubWorkspacesManager manager(Path workspacesRoot) {
        FilesystemVolume volume = new FilesystemVolume(workspacesRoot);
        ProjectManager.Factory projectFactory =
                project -> new HubProjectManager(project, volume);
        return new HubWorkspacesManager(volume,
                workspace -> new HubWorkspaceManager(workspace, ws -> new HubProjectsManager(ws, projectFactory)));
    }

    private static List<String> ids(List<WorkspaceManager> workspaces) {
        return workspaces.stream().map(workspace -> workspace.info().id()).toList();
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

        /** Before the provisioner has written anything, there is no workspace, and no failure. */
        @Test
        void listsNoneUnderARootNothingHasWrittenYet() {
            assertEquals(List.of(), ids(manager(root.resolve("workspaces")).findAll()));
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
