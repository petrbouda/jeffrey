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

package cafe.jeffrey.shared.volume;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A directory tree read through the storage port: every answer keeps "nothing is there" apart
 * from "it could not be looked at".
 */
class FilesystemVolumeTest {

    private static final VolumePath ROOT = VolumePath.root();

    @TempDir
    Path root;

    private FilesystemVolume volume() {
        return new FilesystemVolume(root);
    }

    @Test
    void resolvesAPathUnderItsRoot() {
        assertEquals(root.resolve("ws").resolve("pod-a"), volume().resolve(ROOT.resolve("ws").resolve("pod-a")));
        assertEquals(root, volume().resolve(ROOT));
    }

    @Nested
    class Listing {

        @Test
        void describesEachChildFromOneLook() throws IOException {
            Files.writeString(root.resolve("gc.log"), "twelve bytes");
            Files.createDirectories(root.resolve("pod-a"));

            List<VolumeEntry> entries = volume().list(ROOT).stream()
                    .sorted(Comparator.comparing(VolumeEntry::name))
                    .toList();

            assertEquals(List.of("gc.log", "pod-a"), entries.stream().map(VolumeEntry::name).toList());
            assertEquals(VolumeEntry.Kind.FILE, entries.getFirst().kind());
            assertEquals(12, entries.getFirst().size());
            assertEquals(ROOT.resolve("gc.log"), entries.getFirst().path());
            assertEquals(VolumeEntry.Kind.DIRECTORY, entries.getLast().kind());
        }

        @Test
        void isEmptyWithoutADirectoryThere() throws IOException {
            Files.writeString(root.resolve("a-file"), "x");

            assertEquals(List.of(), volume().list(ROOT.resolve("missing")));
            assertEquals(List.of(), volume().list(ROOT.resolve("a-file")));
        }

        /**
         * async-profiler deletes its cache file as soon as it flushes a chunk, so a name the
         * listing has just read can be gone before it is looked at. A dangling link is that
         * moment held still: listed, and nothing behind it.
         */
        @Test
        void leavesOutAChildThatIsGoneByTheTimeItIsLookedAt() throws IOException {
            Files.writeString(root.resolve("rec-20260912-121559.jfr"), "chunk");
            Files.createSymbolicLink(root.resolve("rec-20260912-121559.jfr.1~"), root.resolve("flushed"));

            List<String> names = volume().list(ROOT).stream().map(VolumeEntry::name).toList();

            assertEquals(List.of("rec-20260912-121559.jfr"), names);
        }
    }

    @Nested
    class Stat {

        @Test
        void isEmptyForNothingThere() {
            assertEquals(Optional.empty(), volume().stat(ROOT.resolve("missing")));
        }

        @Test
        void tellsAFileFromADirectory() throws IOException {
            Files.writeString(root.resolve("marker.json"), "{}");
            Files.createDirectories(root.resolve("pod-a"));

            assertTrue(volume().stat(ROOT.resolve("marker.json")).orElseThrow().isFile());
            assertTrue(volume().stat(ROOT.resolve("pod-a")).orElseThrow().isDirectory());
        }
    }

    @Nested
    class Opening {

        @Test
        void streamsTheFileWithTheNameAndSizeItHasNow() throws IOException {
            Files.writeString(root.resolve("profile-1.jfr.lz4"), "archived chunk");

            try (VolumeAccess.OpenedFile file = volume().open(ROOT.resolve("profile-1.jfr.lz4")).orElseThrow()) {
                assertEquals("profile-1.jfr.lz4", file.name());
                assertEquals(14, file.size());
                assertArrayEquals("archived chunk".getBytes(StandardCharsets.UTF_8), file.content().readAllBytes());
            }
        }

        /** Gone by the time it is asked for — the compression job may have rewritten it. */
        @Test
        void isEmptyForNothingThere() {
            assertEquals(Optional.empty(), volume().open(ROOT.resolve("profile-1.jfr")));
        }
    }

    @Nested
    class Writing {

        @Test
        void writesAFileWholeReplacingWhatWasThereAndLeavesNoScratchFile() throws IOException {
            volume().createDirectories(ROOT.resolve("prod").resolve("orders"));
            VolumePath marker = ROOT.resolve("prod").resolve(".workspace-info.json");

            volume().write(marker, "first".getBytes(StandardCharsets.UTF_8));
            volume().write(marker, "second".getBytes(StandardCharsets.UTF_8));

            assertEquals("second", Files.readString(root.resolve("prod").resolve(".workspace-info.json")));
            try (Stream<Path> left = Files.list(root.resolve("prod"))) {
                assertEquals(List.of(".workspace-info.json", "orders"),
                        left.map(path -> path.getFileName().toString()).sorted().toList());
            }
        }

        @Test
        void deletesADirectoryWithEverythingUnderItAndTreatsWhatIsGoneAsDone() throws IOException {
            Files.createDirectories(root.resolve("prod").resolve("orders"));
            Files.writeString(root.resolve("prod").resolve("orders").resolve("gc.log"), "gc");

            volume().delete(ROOT.resolve("prod"));
            volume().delete(ROOT.resolve("prod"));

            assertFalse(Files.exists(root.resolve("prod")));
        }
    }

    @Nested
    class Reading {

        @Test
        void returnsTheWholeContent() throws IOException {
            Files.writeString(root.resolve("heartbeat"), "1700000000000");

            assertArrayEquals("1700000000000".getBytes(StandardCharsets.UTF_8),
                    volume().read(ROOT.resolve("heartbeat")).orElseThrow());
        }

        @Test
        void isEmptyForNothingThere() {
            assertEquals(Optional.empty(), volume().read(ROOT.resolve("heartbeat")));
        }

        /** Something is there and cannot be read: not the same answer as nothing being there. */
        @Test
        void failsForADirectoryInTheFilesPlace() throws IOException {
            Files.createDirectories(root.resolve("heartbeat"));

            assertThrows(UncheckedIOException.class, () -> volume().read(ROOT.resolve("heartbeat")));
        }

        @Test
        void returnsAFileExactlyAtTheCap() throws IOException {
            Files.write(root.resolve(".session-info.json"), new byte[VolumeAccess.MAX_READ_BYTES]);

            assertEquals(VolumeAccess.MAX_READ_BYTES,
                    volume().read(ROOT.resolve(".session-info.json")).orElseThrow().length);
        }

        /** Whoever writes to the volume must not make a reader hold a file of any size by listing. */
        @Test
        void refusesAFileLargerThanAMarkerCanBe() throws IOException {
            Files.write(root.resolve(".session-info.json"), new byte[VolumeAccess.MAX_READ_BYTES + 1]);

            assertThrows(UncheckedIOException.class, () -> volume().read(ROOT.resolve(".session-info.json")));
        }
    }
}
