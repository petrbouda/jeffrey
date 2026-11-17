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

package cafe.jeffrey.shared.volume.layout;

import cafe.jeffrey.shared.volume.FilesystemVolume;
import cafe.jeffrey.test.volume.VolumeWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which root directories are workspaces: every visible one but the filesystem's own, the one rule
 * Microscope and the volume scheduler both read the volume by. The provisioner's own default
 * workspace, {@code $default}, is one like any other.
 */
class VolumeWorkspaceTest {

    @TempDir
    Path root;

    private FilesystemVolume volume;

    @BeforeEach
    void setUp() throws IOException {
        volume = new FilesystemVolume(root);
        VolumeWriter.workspace(root, "prod-ws");
        VolumeWriter.workspace(root, "$default");
        VolumeWriter.workspace(root, "my_ws");
        Files.createDirectories(root.resolve("lost+found"));
        Files.createDirectories(root.resolve(".scratch"));
        Files.writeString(root.resolve("notes.txt"), "x");
    }

    @Nested
    class All {

        @Test
        void listsEveryVisibleDirectoryButTheFilesystemsOwn() {
            List<String> ids = VolumeWorkspace.all(volume).stream()
                    .map(VolumeWorkspace::id)
                    .sorted()
                    .toList();

            assertEquals(List.of("$default", "my_ws", "prod-ws"), ids);
        }
    }

    @Nested
    class At {

        @Test
        void findsAWorkspaceByItsId() {
            assertTrue(VolumeWorkspace.at(volume, "$default").isPresent());
        }

        @Test
        void theFilesystemsOwnDirectoryIsNoWorkspace() {
            assertTrue(VolumeWorkspace.at(volume, "lost+found").isEmpty());
        }
    }
}
