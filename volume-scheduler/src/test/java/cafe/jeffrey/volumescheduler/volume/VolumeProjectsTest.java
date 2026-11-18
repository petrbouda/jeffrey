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
package cafe.jeffrey.volumescheduler.volume;

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
 * The projects the jobs visit: each visible directory of each workspace, where a workspace is a
 * visible root directory other than the filesystem's own. Nothing in a directory is read, and the
 * volume scheduler creates none: the provisioner lays each one down.
 */
class VolumeProjectsTest {

    @TempDir
    Path root;

    private List<VolumeProject> projects() {
        return projects(root);
    }

    private static List<VolumeProject> projects(Path workspacesRoot) {
        return new VolumeProjects(new FilesystemVolume(workspacesRoot)).all();
    }

    private static List<String> scopes(List<VolumeProject> projects) {
        return projects.stream()
                .map(project -> project.ref().logScope())
                .toList();
    }

    @Nested
    class Workspaces {

        @Test
        void visitsTheProjectsOfEveryWorkspace() {
            VolumeWriter.project(VolumeWriter.workspace(root, "prod-ws"), "orders");
            VolumeWriter.project(VolumeWriter.workspace(root, "$default"), "payments");

            assertEquals(List.of(
                            "workspace_id=$default project_id=payments",
                            "workspace_id=prod-ws project_id=orders"),
                    scopes(projects()).stream().sorted().toList());
        }

        @Test
        void skipsADotDirectoryAndTheFilesystemsOwn() throws IOException {
            VolumeWriter.project(Files.createDirectories(root.resolve(".pending")), "hidden");
            VolumeWriter.project(Files.createDirectories(root.resolve("lost+found")), "recovered");
            VolumeWriter.project(VolumeWriter.workspace(root, "prod-ws"), "orders");

            assertEquals(List.of("workspace_id=prod-ws project_id=orders"), scopes(projects()));
        }

        /** Before the provisioner has written anything, there is no project, and no failure. */
        @Test
        void findsNoneUnderARootNothingHasWrittenYet() {
            assertEquals(List.of(), projects(root.resolve("workspaces")));
        }

        @Test
        void anEmptyWorkspaceHasNoProject() {
            VolumeWriter.workspace(root, "prod-ws");

            assertEquals(List.of(), projects());
        }
    }

    @Nested
    class Projects {

        @Test
        void namesEachProjectByItsWorkspaceAndItsDirectory() {
            VolumeWriter.project(VolumeWriter.workspace(root, "prod-ws"), "orders");

            assertEquals(new ProjectRef("prod-ws", "orders"), projects().getFirst().ref());
        }

        @Test
        void aFileOrAHiddenDirectoryIsNoProject() throws IOException {
            Path workspace = VolumeWriter.workspace(root, "prod-ws");
            Files.createDirectories(workspace.resolve(".scratch"));
            Files.writeString(workspace.resolve("notes.txt"), "x");

            assertEquals(List.of(), projects());
        }
    }
}
