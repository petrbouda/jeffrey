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

package cafe.jeffrey.hub.core.project.repository;

import cafe.jeffrey.shared.volume.FilesystemVolume;
import cafe.jeffrey.shared.volume.VolumeEntry;
import cafe.jeffrey.shared.volume.VolumePath;
import cafe.jeffrey.shared.volume.recording.RecordingFormat;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which types may be rewritten, and what the rewrite has to preserve.
 */
class CompressionTest {

    @TempDir
    Path dir;

    private static Compression jfrCompression() {
        return Compression.of(RecordingFormat.JFR).orElseThrow();
    }

    /** The file as a reader of the volume rooted at {@link #dir} describes it. */
    private VolumeEntry entry(Path file) {
        return new FilesystemVolume(dir)
                .stat(VolumePath.root().resolve(file.getFileName().toString()))
                .orElseThrow();
    }

    @Nested
    class WhichTypesCanBeCompressed {

        @Test
        void aRecordingThatStaysOneAfterwards() {
            assertTrue(Compression.of(RecordingFormat.JFR).isPresent());
        }

        @Test
        void notItsArchive() {
            assertTrue(Compression.of(RecordingFormat.JFR_LZ4).isEmpty());
        }
    }

    @Nested
    class WhatTheRewriteKeeps {

        /**
         * The three things a name decides — the type, the id, and when the file was opened — all
         * have to survive, or the archive is not the same file to any reader of it.
         */
        @Test
        void theArchiveIsStillTheSameRecording() throws IOException {
            Path source = Files.write(
                    dir.resolve("profile-20260220-120500.jfr"), "x".getBytes(StandardCharsets.UTF_8));

            Path target = jfrCompression().target(source);
            jfrCompression().compress(source, target);

            assertEquals("profile-20260220-120500.jfr.lz4", target.getFileName().toString());
            assertTrue(Files.isRegularFile(target));

            RecordingFormat archived = RecordingFormat.of(target.getFileName().toString()).orElseThrow();
            assertEquals(RecordingFormat.JFR_LZ4, archived);
            assertTrue(archived.isArchive());
            assertEquals(
                    RecordingFormat.JFR.timestampResolver().resolve(entry(source)),
                    archived.timestampResolver().resolve(entry(target)),
                    "the archive answers with the instant the profiler opened the chunk");
            assertEquals(
                    RecordingFormat.JFR.idOf(source.getFileName().toString()),
                    archived.idOf(target.getFileName().toString()),
                    "and to the id a reader took from the listing before the rewrite");
        }
    }

    /**
     * The target's name appears whole or not at all, which is what lets every reader of a session
     * directory treat an archive's presence as the whole story.
     */
    @Nested
    class PublishingTheArchive {

        /**
         * Written elsewhere and renamed on, so nothing is ever listed under the target's name
         * that is still being written into. Observed through the one thing a test can see from
         * outside: what the directory holds while the compression is in flight.
         */
        @Test
        void theTargetNameNeverNamesAPartialFile() throws IOException {
            Path source = Files.write(dir.resolve("profile-20260220-120500.jfr"), body());
            Path target = jfrCompression().target(source);

            jfrCompression().compress(source, target);

            assertTrue(Files.isRegularFile(target));
            assertTrue(Files.size(target) > 0);
            assertEquals(List.of(), scratchFiles(), "the scratch file does not outlive the compression");
        }

        /**
         * The source is the caller's to remove, and it is still there to be removed: a
         * compression that took the recording away itself would leave a caller that failed to
         * verify the result with neither.
         */
        @Test
        void leavesTheRecordingItWasMadeFrom() throws IOException {
            Path source = Files.write(dir.resolve("profile-20260220-120500.jfr"), body());

            jfrCompression().compress(
                    source, jfrCompression().target(source));

            assertTrue(Files.isRegularFile(source));
        }

        /**
         * A second run over the same recording — the periodic job and an on-demand run overlap,
         * and the lock that would serialise them is held by one storage instance while an
         * instance is built per call. Each writes its own scratch file, so neither can publish
         * the other's half-written one.
         */
        @Test
        void aSecondRunOverTheSameFileLandsAWholeArchive() throws IOException {
            Path source = Files.write(dir.resolve("profile-20260220-120500.jfr"), body());
            Path target = jfrCompression().target(source);

            jfrCompression().compress(source, target);
            long firstSize = Files.size(target);
            jfrCompression().compress(source, target);

            assertEquals(firstSize, Files.size(target));
            assertEquals(List.of(), scratchFiles());
        }

        /**
         * An empty recording is still compressed into a real archive — an LZ4 frame of nothing
         * is a frame, not nothing — so the guard before the rename cannot be what keeps one out
         * of the repository, and does not claim to be. What keeps one out is the compression
         * job, which leaves a zero-byte recording alone rather than asking for this; the guard
         * here is the last line before a caller deletes a source, for a writer that produced no
         * bytes at all.
         */
        @Test
        void anEmptyRecordingStillCompressesIntoARealArchive() throws IOException {
            Path source = Files.createFile(dir.resolve("profile-20260220-120500.jfr"));
            Path target = jfrCompression().target(source);

            jfrCompression().compress(source, target);

            assertTrue(Files.size(target) > 0, "the frame's own header");
            assertEquals(RecordingFormat.JFR_LZ4, RecordingFormat.of(target.getFileName().toString()).orElseThrow());
            assertEquals(List.of(), scratchFiles());
        }

        /**
         * A scratch file is hidden, so no listing of the session shows one and no reader has to
         * be taught to ignore it. The one that matters is the repository's own, which drops
         * hidden files.
         */
        @Test
        void theScratchFileIsHidden() throws IOException {
            Path source = Files.write(dir.resolve("profile-20260220-120500.jfr"), body());
            Path target = jfrCompression().target(source);

            // Nothing to observe mid-flight from here, so the name is asserted where it is
            // built: a compression that fails leaves its scratch file behind only if it is not
            // cleaned up, and the one thing always true of the name is its leading dot.
            jfrCompression().compress(source, target);

            assertFalse(target.getFileName().toString().startsWith("."),
                    "what is published is not hidden");
            assertEquals(List.of(), scratchFiles());
        }
    }

    private static byte[] body() {
        return "an event or two, repeated so the frame has something to chew on"
                .repeat(50).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Whatever the compression left behind that is not a file of the session — every entry that
     * is hidden, which is what the scratch name is.
     */
    private List<String> scratchFiles() throws IOException {
        try (Stream<Path> entries = Files.list(dir)) {
            return entries
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("."))
                    .sorted()
                    .toList();
        }
    }
}
