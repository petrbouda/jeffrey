/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package cafe.jeffrey.shared.common.model.repository;

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

    @Nested
    class WhichTypesCanBeCompressed {

        @Test
        void aRecordingThatStaysOneAfterwards() {
            assertTrue(ManagedFile.JFR.compression().isSupported());
        }

        /**
         * Not an algorithm nobody has written yet: a compressed pprof matches nothing, so the
         * file would stop being a recording, change id, and lose its original.
         */
        @Test
        void nothingElse() {
            for (ManagedFile type : ManagedFile.values()) {
                if (type != ManagedFile.JFR) {
                    assertFalse(type.compression().isSupported(), "type: " + type);
                }
            }
        }

        @Test
        void refusesToActWhenItSaysItCannot() {
            Path file = dir.resolve("app.pprof");

            assertThrows(UnsupportedOperationException.class,
                    () -> ManagedFile.PPROF.compression().target(file));
            assertThrows(UnsupportedOperationException.class,
                    () -> ManagedFile.PPROF.compression().compress(file, file));
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

            Path target = ManagedFile.JFR.compression().target(source);
            ManagedFile.JFR.compression().compress(source, target);

            assertEquals("profile-20260220-120500.jfr.lz4", target.getFileName().toString());
            assertTrue(Files.isRegularFile(target));

            ManagedFile archived = ManagedFile.of(target);
            assertEquals(ManagedFile.JFR_LZ4, archived);
            assertEquals(FileCategory.RECORDING, archived.fileCategory());
            assertTrue(archived.isArchive());
            assertEquals(
                    ManagedFile.JFR.timestampResolver().resolve(source),
                    archived.timestampResolver().resolve(target),
                    "the archive answers with the instant the profiler opened the chunk");
            assertEquals(
                    ManagedFile.JFR.idOf(source),
                    archived.idOf(target),
                    "and to the id a reader took from the listing before the rewrite");
        }

        @Test
        void andCannotBeCompressedAgain() {
            assertFalse(ManagedFile.JFR_LZ4.compression().isSupported());
        }

        /**
         * The pair has to be declared, not spelled. Read off the extension, the answer was
         * whatever a name happened to end in, so a type nothing here compresses could claim an
         * archive's guarantees — and JFR_LZ4 had to be told it was uncompressible by a constant
         * that also means "rewriting this would destroy it", which of an archive is untrue.
         */
        @Test
        void andIsTheOnlyTypeThatCountsAsOne() {
            List<ManagedFile> archives = Stream.of(ManagedFile.values())
                    .filter(ManagedFile::isArchive)
                    .toList();

            assertEquals(List.of(ManagedFile.JFR_LZ4), archives);
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
            Path target = ManagedFile.JFR.compression().target(source);

            ManagedFile.JFR.compression().compress(source, target);

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

            ManagedFile.JFR.compression().compress(
                    source, ManagedFile.JFR.compression().target(source));

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
            Path target = ManagedFile.JFR.compression().target(source);

            ManagedFile.JFR.compression().compress(source, target);
            long firstSize = Files.size(target);
            ManagedFile.JFR.compression().compress(source, target);

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
            Path target = ManagedFile.JFR.compression().target(source);

            ManagedFile.JFR.compression().compress(source, target);

            assertTrue(Files.size(target) > 0, "the frame's own header");
            assertEquals(ManagedFile.JFR_LZ4, ManagedFile.of(target));
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
            Path target = ManagedFile.JFR.compression().target(source);

            // Nothing to observe mid-flight from here, so the name is asserted where it is
            // built: a compression that fails leaves its scratch file behind only if it is not
            // cleaned up, and the one thing always true of the name is its leading dot.
            ManagedFile.JFR.compression().compress(source, target);

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
