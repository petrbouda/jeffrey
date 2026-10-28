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

package cafe.jeffrey.hub.core.manager.project;

import cafe.jeffrey.hub.core.volume.TestVolume;
import cafe.jeffrey.hub.core.volume.WorkspaceDirectory;
import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.shared.volume.FilesystemVolume;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A workspace's projects are its visible child directories, each known by its directory name.
 */
class HubProjectsManagerTest {

    private static final Instant T0 = Instant.parse("2026-01-15T10:00:00Z");

    @TempDir
    Path root;

    private Path workspaceDir;

    private HubProjectsManager projects;

    @BeforeEach
    void setUp() {
        workspaceDir = TestVolume.workspace(root, "prod-ws");
        FilesystemVolume volume = new FilesystemVolume(root);
        projects = new HubProjectsManager(
                new WorkspaceDirectory(volume, TestVolume.volumePath(root, workspaceDir), T0),
                project -> new HubProjectManager(project, volume));
    }

    @Test
    void readsEachProjectFromItsDirectory() {
        TestVolume.project(workspaceDir, "orders");

        ProjectInfo info = projects.findAll().getFirst().info();
        assertEquals("orders", info.id());
        assertEquals("prod-ws", info.workspaceId());
        assertNotNull(info.createdAt(), "its directory's time, as the mount gives it");
    }

    @Test
    void aFileOrAHiddenDirectoryIsNoProject() throws IOException {
        Files.createDirectories(workspaceDir.resolve(".scratch"));
        Files.writeString(workspaceDir.resolve("notes.txt"), "x");

        assertTrue(projects.findAll().isEmpty());
    }
}
