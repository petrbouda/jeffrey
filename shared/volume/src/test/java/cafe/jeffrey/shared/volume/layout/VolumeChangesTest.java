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
import cafe.jeffrey.shared.volume.VolumePath;
import cafe.jeffrey.test.volume.VolumeWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VolumeChangesTest {

    private static final Instant T0 = Instant.parse("2026-02-20T12:00:00Z");
    private static final VolumePath WORKSPACE = VolumePath.root().resolve("prod");

    @TempDir
    Path root;

    private FilesystemVolume volume;
    private Path projectDir;

    @BeforeEach
    void setUp() throws IOException {
        volume = new FilesystemVolume(root);
        projectDir = VolumeWriter.project(Files.createDirectories(root.resolve("prod")), "orders");
    }

    private ProjectTree project() {
        return ProjectTree.at(volume, WORKSPACE.resolve(projectDir.getFileName().toString())).orElseThrow();
    }

    @Nested
    class Workspace {

        @Test
        void isItsDirectory() throws IOException {
            assertTrue(VolumeChanges.createWorkspace(volume, "staging"));

            VolumeWorkspace created = VolumeWorkspace.at(volume, "staging").orElseThrow();
            assertEquals("staging", created.id());
            assertTrue(created.createdAt().isPresent(), "its directory's time, as the volume lists it");
            try (var left = Files.list(root.resolve("staging"))) {
                assertEquals(0, left.count(), "nothing is written into it");
            }
        }

        @Test
        void isRefusedForAnIdWhoseDirectoryIsThere() {
            assertFalse(VolumeChanges.createWorkspace(volume, "prod"));
            assertTrue(Files.isDirectory(projectDir), "the project the provisioner wrote there stays");
        }

        @Test
        void areListedByTheirDirectoriesAlone() throws IOException {
            Files.createDirectories(root.resolve(".scratch"));
            VolumeChanges.createWorkspace(volume, "staging");

            assertEquals(List.of("prod", "staging"), VolumeWorkspace.all(volume).stream().map(VolumeWorkspace::id).toList());
            assertEquals(Optional.empty(), VolumeWorkspace.at(volume, "missing"));
            assertEquals(Optional.empty(), VolumeWorkspace.at(volume, "../prod"));
        }
    }

    @Nested
    class DeletingASession {

        @Test
        void takesTheInstanceWithItsLastSession() {
            Path instance = VolumeWriter.instance(projectDir, "pod-a");
            String id = VolumeWriter.session(instance, T0).getFileName().toString();

            assertEquals(SessionDeletion.SESSION_AND_INSTANCE_DELETED, VolumeChanges.deleteSession(volume, project(), id));
            assertFalse(Files.exists(instance));
        }

        /** Any directory left in the instance keeps it, whatever it is named. */
        @Test
        void keepsTheInstanceWhileAnyDirectoryIsLeftInIt() throws IOException {
            Path instance = VolumeWriter.instance(projectDir, "pod-a");
            String id = VolumeWriter.session(instance, T0).getFileName().toString();
            Files.createDirectories(instance.resolve("about-to-start"));

            assertEquals(SessionDeletion.SESSION_DELETED, VolumeChanges.deleteSession(volume, project(), id));
            assertFalse(Files.exists(instance.resolve(id)));
            assertTrue(Files.isDirectory(instance.resolve("about-to-start")));
        }

        @Test
        void isNotFoundForASessionTheProjectDoesNotHold() {
            assertEquals(SessionDeletion.NOT_FOUND, VolumeChanges.deleteSession(volume, project(), "missing"));
        }
    }
}
