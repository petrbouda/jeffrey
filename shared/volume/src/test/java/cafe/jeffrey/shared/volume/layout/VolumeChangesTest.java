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

import cafe.jeffrey.shared.common.Json;
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
        projectDir = VolumeWriter.project(Files.createDirectories(root.resolve("prod")), "p-orders", "orders", T0);
    }

    private ProjectTree project() {
        return ProjectTree.at(volume, WORKSPACE.resolve(projectDir.getFileName().toString())).orElseThrow();
    }

    @Nested
    class Workspace {

        @Test
        void isDescribedByItsMarkerWrittenWhole() throws IOException {
            assertFalse(VolumeChanges.isDescribedWorkspace(volume, WORKSPACE));

            VolumeChanges.describeWorkspace(volume, WORKSPACE, new WorkspaceMarker("Production", T0.toEpochMilli()));

            assertTrue(VolumeChanges.isDescribedWorkspace(volume, WORKSPACE));
            assertEquals(new WorkspaceMarker("Production", T0.toEpochMilli()), Json.read(
                    Files.readString(root.resolve("prod").resolve(WorkspaceMarker.FILE_NAME)), WorkspaceMarker.class));
            try (var left = Files.list(root.resolve("prod"))) {
                assertTrue(left.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")));
            }
        }

        @Test
        void createsTheDirectoryWhenNoneIsThere() {
            VolumePath fresh = VolumePath.root().resolve("staging");

            VolumeChanges.describeWorkspace(volume, fresh, new WorkspaceMarker("Staging", T0.toEpochMilli()));

            assertTrue(VolumeChanges.isDescribedWorkspace(volume, fresh));
        }

        @Test
        void isCreatedAdoptingTheDirectoryTheProvisionerWroteInto() {
            assertEquals(WorkspaceCreation.CREATED,
                    VolumeChanges.createWorkspace(volume, "prod", "Production", T0.toEpochMilli()));

            VolumeWorkspace created = VolumeWorkspace.at(volume, WORKSPACE);
            assertEquals("Production", created.name());
            assertEquals(Optional.of(T0), created.createdAt());
            assertTrue(created.described());
            assertTrue(Files.isDirectory(projectDir), "the project already written there stays");
        }

        @Test
        void isRefusedForAnIdAlreadyDescribed() {
            VolumeChanges.createWorkspace(volume, "prod", "Production", T0.toEpochMilli());

            assertEquals(WorkspaceCreation.ID_TAKEN,
                    VolumeChanges.createWorkspace(volume, "prod", "Another", T0.toEpochMilli()));
        }

        @Test
        void isRefusedForANameAnotherWorkspaceGoesBy() {
            VolumeChanges.createWorkspace(volume, "prod", "Production", T0.toEpochMilli());

            assertEquals(WorkspaceCreation.NAME_TAKEN,
                    VolumeChanges.createWorkspace(volume, "staging", "Production", T0.toEpochMilli()));
            assertFalse(VolumeChanges.isDescribedWorkspace(volume, VolumePath.root().resolve("staging")));
        }

        @Test
        void anUndescribedDirectoryGoesByItsId() {
            VolumeWorkspace undescribed = VolumeWorkspace.at(volume, WORKSPACE);

            assertEquals("prod", undescribed.name());
            assertFalse(undescribed.described());
            assertEquals(WorkspaceCreation.NAME_TAKEN,
                    VolumeChanges.createWorkspace(volume, "staging", "prod", T0.toEpochMilli()));
        }
    }

    @Nested
    class Pinning {

        @Test
        void setsAndClearsTheRetainedFlag() {
            Path session = VolumeWriter.session(VolumeWriter.instance(projectDir, "pod-a", T0), "s-1", T0, null);

            assertTrue(VolumeChanges.setRetained(volume, project(), "s-1", true));
            assertTrue(Files.exists(session.resolve(SessionDirectory.RETAINED_FLAG)));
            assertTrue(VolumeChanges.setRetained(volume, project(), "s-1", true), "setting it twice is no error");

            assertTrue(VolumeChanges.setRetained(volume, project(), "s-1", false));
            assertFalse(Files.exists(session.resolve(SessionDirectory.RETAINED_FLAG)));
        }

        @Test
        void isFalseForASessionTheProjectDoesNotHold() {
            assertFalse(VolumeChanges.setRetained(volume, project(), "missing", true));
        }
    }

    @Nested
    class DeletingASession {

        @Test
        void takesTheInstanceWithItsLastSession() {
            Path instance = VolumeWriter.instance(projectDir, "pod-a", T0);
            VolumeWriter.session(instance, "s-1", T0, null);

            assertEquals(SessionDeletion.SESSION_AND_INSTANCE_DELETED, VolumeChanges.deleteSession(volume, project(), "s-1"));
            assertFalse(Files.exists(instance));
        }

        /** A directory the provisioner has not described yet is a session about to start. */
        @Test
        void keepsTheInstanceWhileAnyDirectoryIsLeftInIt() throws IOException {
            Path instance = VolumeWriter.instance(projectDir, "pod-a", T0);
            VolumeWriter.session(instance, "s-1", T0, null);
            Files.createDirectories(instance.resolve("s-2"));

            assertEquals(SessionDeletion.SESSION_DELETED, VolumeChanges.deleteSession(volume, project(), "s-1"));
            assertFalse(Files.exists(instance.resolve("s-1")));
            assertTrue(Files.isDirectory(instance.resolve("s-2")));
        }

        @Test
        void isNotFoundForASessionTheProjectDoesNotHold() {
            assertEquals(SessionDeletion.NOT_FOUND, VolumeChanges.deleteSession(volume, project(), "missing"));
        }
    }
}
