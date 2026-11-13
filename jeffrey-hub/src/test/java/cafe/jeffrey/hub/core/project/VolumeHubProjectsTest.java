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
package cafe.jeffrey.hub.core.project;

import cafe.jeffrey.hub.core.volume.TestVolume;
import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.shared.volume.FilesystemVolume;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The projects the hub's jobs visit: each visible directory of each workspace, where a workspace
 * is a root directory named by a valid reference id. Nothing in a directory is read, and the hub
 * creates none: the provisioner lays each one down.
 */
class VolumeHubProjectsTest {

    @TempDir
    Path root;

    private List<HubProject> projects() {
        return projects(root);
    }

    private static List<HubProject> projects(Path workspacesRoot) {
        return new VolumeHubProjects(new FilesystemVolume(workspacesRoot)).all();
    }

    private static List<String> scopes(List<HubProject> projects) {
        return projects.stream()
                .map(HubProject::logScope)
                .toList();
    }

    @Nested
    class Workspaces {

        @Test
        void visitsTheProjectsOfEveryWorkspace() {
            TestVolume.project(TestVolume.workspace(root, "prod-ws"), "orders");
            TestVolume.project(TestVolume.workspace(root, "staging-ws"), "payments");

            assertEquals(List.of(
                            "workspace_id=prod-ws project_id=orders",
                            "workspace_id=staging-ws project_id=payments"),
                    scopes(projects()));
        }

        @Test
        void skipsADotDirectoryAndOneNotNamedByAReferenceId() throws IOException {
            TestVolume.project(Files.createDirectories(root.resolve(".pending")), "hidden");
            TestVolume.project(Files.createDirectories(root.resolve("-not-a-reference-id")), "invalid");
            TestVolume.project(TestVolume.workspace(root, "prod-ws"), "orders");

            assertEquals(List.of("workspace_id=prod-ws project_id=orders"), scopes(projects()));
        }

        /** Before the provisioner has written anything, there is no project, and no failure. */
        @Test
        void findsNoneUnderARootNothingHasWrittenYet() {
            assertEquals(List.of(), projects(root.resolve("workspaces")));
        }

        @Test
        void anEmptyWorkspaceHasNoProject() {
            TestVolume.workspace(root, "prod-ws");

            assertEquals(List.of(), projects());
        }
    }

    @Nested
    class Projects {

        @Test
        void readsEachProjectFromItsDirectory() {
            TestVolume.project(TestVolume.workspace(root, "prod-ws"), "orders");

            ProjectInfo info = projects().getFirst().info();
            assertEquals("orders", info.id());
            assertEquals("prod-ws", info.workspaceId());
            assertNotNull(info.createdAt(), "its directory's time, as the mount gives it");
        }

        @Test
        void aFileOrAHiddenDirectoryIsNoProject() throws IOException {
            Path workspace = TestVolume.workspace(root, "prod-ws");
            Files.createDirectories(workspace.resolve(".scratch"));
            Files.writeString(workspace.resolve("notes.txt"), "x");

            assertEquals(List.of(), projects());
        }
    }
}
