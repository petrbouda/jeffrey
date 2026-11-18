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

package cafe.jeffrey.microscope.connector.local;

import cafe.jeffrey.shared.volume.VolumeAccess;
import cafe.jeffrey.shared.volume.VolumeEntry;
import cafe.jeffrey.shared.volume.VolumePath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A local volume reads its own tree as a filesystem volume does, and nothing a link in it points to
 * outside the root.
 */
class ContainedFilesystemVolumeTest {

    private static final VolumePath ROOT = VolumePath.root();
    private static final VolumePath PROD = ROOT.resolve("prod");
    private static final VolumePath ESCAPE = ROOT.resolve("escape");

    @TempDir
    Path temp;

    private Path root;
    private Path outside;
    private ContainedFilesystemVolume volume;

    @BeforeEach
    void setUp() throws IOException {
        root = Files.createDirectories(temp.resolve("volume"));
        outside = Files.createDirectories(temp.resolve("outside"));
        Files.writeString(outside.resolve("secret.txt"), "secret");
        Files.createDirectories(root.resolve("prod").resolve("orders"));
        Files.writeString(root.resolve("prod").resolve("notes.txt"), "notes");
        volume = new ContainedFilesystemVolume(root);
    }

    private static List<String> names(List<VolumeEntry> entries) {
        return entries.stream().map(VolumeEntry::name).sorted().toList();
    }

    @Nested
    class InsideTheRoot {

        @Test
        void listsItsTreeAsItLies() {
            assertEquals(List.of("prod"), names(volume.list(ROOT)));
            assertEquals(List.of("notes.txt", "orders"), names(volume.list(PROD)));
        }

        @Test
        void opensAFileInIt() throws IOException {
            try (VolumeAccess.OpenedFile file = volume.open(PROD.resolve("notes.txt")).orElseThrow()) {
                assertEquals("notes", new String(file.content().readAllBytes(), StandardCharsets.UTF_8));
            }
        }

        /** Read live: what appears after a listing is in the next one. */
        @Test
        void seesWhatAppearsBetweenTwoListings() throws IOException {
            assertEquals(List.of("prod"), names(volume.list(ROOT)));

            Files.createDirectories(root.resolve("staging"));

            assertEquals(List.of("prod", "staging"), names(volume.list(ROOT)));
        }

        @Test
        void followsALinkThatStaysInside() throws IOException {
            Files.createSymbolicLink(root.resolve("alias"), root.resolve("prod"));

            assertEquals(List.of("alias", "prod"), names(volume.list(ROOT)));
            assertTrue(volume.stat(ROOT.resolve("alias").resolve("notes.txt")).isPresent());
        }

        @Test
        void deletesADirectoryWithEverythingUnderIt() {
            volume.delete(PROD);

            assertFalse(Files.exists(root.resolve("prod")));
        }

        @Test
        void nothingThereReadsAsAbsent() {
            assertTrue(volume.stat(ROOT.resolve("missing")).isEmpty());
            assertTrue(volume.open(ROOT.resolve("missing")).isEmpty());
            assertEquals(List.of(), volume.list(ROOT.resolve("missing")));
        }
    }

    /**
     * A link out lying deep in a directory that is itself inside: the containment check covers the
     * directory, so what keeps the target safe is that a directory delete walks the tree without
     * following links and removes the link itself.
     */
    @Nested
    class LinkOutDeepInTheTree {

        private static final VolumePath ORDERS = PROD.resolve("orders");

        @BeforeEach
        void linkOut() throws IOException {
            Files.createSymbolicLink(root.resolve("prod").resolve("orders").resolve("escape"), outside);
        }

        @Test
        void isLeftOutOfItsDirectorysListing() {
            assertEquals(List.of(), names(volume.list(ORDERS)));
        }

        @Test
        void readsAsAbsent() {
            assertTrue(volume.open(ORDERS.resolve("escape").resolve("secret.txt")).isEmpty());
        }

        @Test
        void deletingTheDirectoryAboveItRemovesTheLinkAndNeverItsTarget() {
            volume.delete(PROD);

            assertFalse(Files.exists(root.resolve("prod")));
            assertTrue(Files.exists(outside.resolve("secret.txt")));
        }
    }

    /** A relative link that climbs out of the root through its parents is a link out like any other. */
    @Nested
    class RelativeLinkClimbingOut {

        private static final VolumePath BACK = PROD.resolve("back");

        @BeforeEach
        void linkUp() throws IOException {
            Files.createSymbolicLink(root.resolve("prod").resolve("back"), Path.of("..", ".."));
        }

        @Test
        void readsNothingThroughIt() {
            assertTrue(volume.stat(BACK.resolve("outside").resolve("secret.txt")).isEmpty());
            assertTrue(volume.open(BACK.resolve("outside").resolve("secret.txt")).isEmpty());
            assertEquals(List.of(), volume.list(BACK));
        }

        @Test
        void deletesNothingThroughIt() {
            volume.delete(BACK.resolve("outside").resolve("secret.txt"));
            volume.delete(BACK.resolve("outside"));

            assertTrue(Files.exists(outside.resolve("secret.txt")));
        }
    }

    @Nested
    class LinkOutOfTheRoot {

        @BeforeEach
        void linkOut() throws IOException {
            Files.createSymbolicLink(root.resolve("escape"), outside);
        }

        @Test
        void isLeftOutOfTheListing() {
            assertEquals(List.of("prod"), names(volume.list(ROOT)));
        }

        @Test
        void isNotListedThrough() {
            assertEquals(List.of(), volume.list(ESCAPE));
        }

        @Test
        void readsAsAbsent() {
            assertTrue(volume.stat(ESCAPE).isEmpty());
            assertTrue(volume.stat(ESCAPE.resolve("secret.txt")).isEmpty());
            assertTrue(volume.open(ESCAPE.resolve("secret.txt")).isEmpty());
        }

        /** A delete through the link never reaches what it points to. */
        @Test
        void isNotDeletedThrough() {
            volume.delete(ESCAPE.resolve("secret.txt"));
            volume.delete(ESCAPE);

            assertTrue(Files.exists(outside.resolve("secret.txt")));
        }
    }
}
