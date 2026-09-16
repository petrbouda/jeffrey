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
            assertTrue(archived.isCompressed());
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
    }
}
