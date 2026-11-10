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
import cafe.jeffrey.shared.volume.VolumeEntry;
import cafe.jeffrey.shared.volume.VolumePath;
import cafe.jeffrey.shared.volume.WritableVolume;
import cafe.jeffrey.test.volume.VolumeWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
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

    /** A workspace is the directory the provisioner laid down: nothing here creates one. */
    @Nested
    class Workspace {

        @Test
        void isItsDirectory() throws IOException {
            Files.createDirectories(root.resolve("staging"));

            VolumeWorkspace staging = VolumeWorkspace.at(volume, "staging").orElseThrow();
            assertEquals("staging", staging.id());
            assertTrue(staging.createdAt().isPresent(), "its directory's time, as the volume lists it");
        }

        @Test
        void areListedByTheirDirectoriesAlone() throws IOException {
            Files.createDirectories(root.resolve(".scratch"));
            Files.createDirectories(root.resolve("staging"));

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

    /**
     * The instance goes with its last session only when one listing says nothing is left. A
     * listing that fails says nothing about what is there: read as empty, it would delete the
     * instance with every other session in it, the live one too.
     */
    @Nested
    class WhenTheInstanceCannotBeListedAfterTheDelete {

        @Test
        void keepsTheInstanceAndItsOtherSessions() {
            Path instance = VolumeWriter.instance(projectDir, "pod-a");
            String older = VolumeWriter.session(instance, T0).getFileName().toString();
            Path live = VolumeWriter.session(instance, T0.plusSeconds(600));
            FailsToListAfterADelete flaky = new FailsToListAfterADelete(volume, instancePath("pod-a"));

            assertEquals(SessionDeletion.SESSION_DELETED, VolumeChanges.deleteSession(flaky, project(flaky), older));
            assertFalse(Files.exists(instance.resolve(older)));
            assertTrue(Files.isDirectory(live), "the live session must survive a listing that failed");
        }

        @Test
        void keepsTheInstanceEvenAfterItsLastSession() {
            Path instance = VolumeWriter.instance(projectDir, "pod-a");
            String last = VolumeWriter.session(instance, T0).getFileName().toString();
            FailsToListAfterADelete flaky = new FailsToListAfterADelete(volume, instancePath("pod-a"));

            assertEquals(SessionDeletion.SESSION_DELETED, VolumeChanges.deleteSession(flaky, project(flaky), last));
            assertTrue(Files.isDirectory(instance), "an instance that cannot be listed is kept for a later pass");
        }

        private VolumePath instancePath(String instanceId) {
            return WORKSPACE.resolve(projectDir.getFileName().toString()).resolve(instanceId);
        }

        private ProjectTree project(WritableVolume over) {
            return ProjectTree.at(over, WORKSPACE.resolve(projectDir.getFileName().toString())).orElseThrow();
        }
    }

    /** The volume, except that one directory cannot be listed once anything has been deleted. */
    private static final class FailsToListAfterADelete implements WritableVolume {

        private final WritableVolume delegate;
        private final VolumePath unlistable;
        private boolean deleted;

        private FailsToListAfterADelete(WritableVolume delegate, VolumePath unlistable) {
            this.delegate = delegate;
            this.unlistable = unlistable;
        }

        @Override
        public List<VolumeEntry> list(VolumePath directory) {
            if (deleted && directory.equals(unlistable)) {
                throw new UncheckedIOException(new IOException("Stale file handle: " + directory));
            }
            return delegate.list(directory);
        }

        @Override
        public Optional<VolumeEntry> stat(VolumePath path) {
            return delegate.stat(path);
        }

        @Override
        public Optional<OpenedFile> open(VolumePath file) {
            return delegate.open(file);
        }

        @Override
        public void delete(VolumePath path) {
            delegate.delete(path);
            deleted = true;
        }
    }
}
