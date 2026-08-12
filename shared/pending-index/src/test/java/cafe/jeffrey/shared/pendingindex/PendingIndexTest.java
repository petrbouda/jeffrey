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

package cafe.jeffrey.shared.pendingindex;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PendingIndexTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-02-20T15:30:45.123Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    private static final PendingIndexEntryParser<String> IDENTITY = (_, content) -> Optional.of(content);

    @TempDir
    Path tempDir;

    @Nested
    class Add {

        @Test
        void writesFileWithContent() throws IOException {
            Path indexDir = tempDir.resolve(".pending");
            PendingIndex index = new PendingIndex(indexDir, FIXED_CLOCK);

            index.add("aaaaaaaa", "proj-alpha/inst-001");

            try (var files = Files.list(indexDir)) {
                Path written = files.findFirst().orElseThrow();
                assertEquals("20260220153045123_aaaaaaaa", written.getFileName().toString());
                assertEquals("proj-alpha/inst-001", Files.readString(written));
            }
        }

        @Test
        void createsIndexDirectoryOnDemand() {
            Path indexDir = tempDir.resolve("missing").resolve(".pending");
            PendingIndex index = new PendingIndex(indexDir, FIXED_CLOCK);

            index.add("aaaaaaaa", "proj-alpha");

            assertTrue(Files.isDirectory(indexDir));
        }

        /**
         * The reader inspects an index directory in every workspace, most of which never have
         * one. Listing must not litter the volume with empty directories.
         */
        @Test
        void listingDoesNotCreateTheDirectory() {
            Path indexDir = tempDir.resolve(".pending");
            PendingIndex index = new PendingIndex(indexDir, FIXED_CLOCK);

            index.list(IDENTITY);

            assertFalse(Files.exists(indexDir));
        }
    }

    @Nested
    class ListEntries {

        @Test
        void returnsEntriesOldestFirst() throws IOException {
            Path indexDir = tempDir.resolve(".pending");
            Files.createDirectories(indexDir);
            Files.writeString(indexDir.resolve("20260220153045200_aaaaaaaa"), "second");
            Files.writeString(indexDir.resolve("20260220153045100_bbbbbbbb"), "first");
            Files.writeString(indexDir.resolve("20260220153045300_cccccccc"), "third");

            PendingIndex index = new PendingIndex(indexDir, FIXED_CLOCK);
            List<PendingIndexEntry<String>> entries = index.list(IDENTITY);

            assertEquals(3, entries.size());
            assertEquals("first", entries.get(0).parsed());
            assertEquals("second", entries.get(1).parsed());
            assertEquals("third", entries.get(2).parsed());
        }

        @Test
        void skipsEntriesTheParserRejects() throws IOException {
            Path indexDir = tempDir.resolve(".pending");
            Files.createDirectories(indexDir);
            Files.writeString(indexDir.resolve("20260220153045100_aaaaaaaa"), "valid");
            Files.writeString(indexDir.resolve("20260220153045200_bbbbbbbb"), "invalid");

            PendingIndex index = new PendingIndex(indexDir, FIXED_CLOCK);
            List<PendingIndexEntry<String>> entries = index.list(
                    (_, content) -> "valid".equals(content) ? Optional.of(content) : Optional.empty());

            assertEquals(1, entries.size());
            assertEquals("valid", entries.getFirst().parsed());
        }

        @Test
        void skipsHiddenFilesAndSubdirectories() throws IOException {
            Path indexDir = tempDir.resolve(".pending");
            Files.createDirectories(indexDir.resolve("some-subdir"));
            Files.writeString(indexDir.resolve("20260220153045100_aaaaaaaa"), "visible");
            Files.writeString(indexDir.resolve(".hidden-file"), "hidden");

            PendingIndex index = new PendingIndex(indexDir, FIXED_CLOCK);
            List<PendingIndexEntry<String>> entries = index.list(IDENTITY);

            assertEquals(1, entries.size());
            assertEquals("visible", entries.getFirst().parsed());
        }

        @Test
        void returnsEmptyWhenDirectoryDoesNotExist() {
            PendingIndex index = new PendingIndex(tempDir.resolve("nonexistent"), FIXED_CLOCK);

            assertTrue(index.list(IDENTITY).isEmpty());
        }

        @Test
        void entryCarriesItsFilenameAndPath() throws IOException {
            Path indexDir = tempDir.resolve(".pending");
            Files.createDirectories(indexDir);
            String filename = "20260220153045100_aaaaaaaa";
            Files.writeString(indexDir.resolve(filename), "content");

            PendingIndex index = new PendingIndex(indexDir, FIXED_CLOCK);
            List<PendingIndexEntry<String>> entries = index.list(IDENTITY);

            assertEquals(1, entries.size());
            assertEquals(filename, entries.getFirst().filename());
            assertEquals(indexDir.resolve(filename), entries.getFirst().filePath());
        }
    }

    @Nested
    class Remove {

        @Test
        void deletesTheEntry() throws IOException {
            Path indexDir = tempDir.resolve(".pending");
            Files.createDirectories(indexDir);
            Path entry = indexDir.resolve("20260220153045100_aaaaaaaa");
            Files.writeString(entry, "proj-alpha");

            PendingIndex index = new PendingIndex(indexDir, FIXED_CLOCK);
            index.remove(entry);

            assertFalse(Files.exists(entry));
            assertTrue(index.list(IDENTITY).isEmpty());
        }

        /**
         * A reader that crashed between doing the work and removing the entry repeats both on
         * the next tick — the second removal must not fail.
         */
        @Test
        void removingAnAlreadyGoneEntryIsANoOp() {
            Path indexDir = tempDir.resolve(".pending");
            PendingIndex index = new PendingIndex(indexDir, FIXED_CLOCK);

            assertDoesNotThrow(() -> index.remove(indexDir.resolve("20260220153045100_aaaaaaaa")));
        }

        @Test
        void removesOnlyTheNamedEntry() throws IOException {
            Path indexDir = tempDir.resolve(".pending");
            Files.createDirectories(indexDir);
            Path first = indexDir.resolve("20260220153045100_aaaaaaaa");
            Path second = indexDir.resolve("20260220153045200_bbbbbbbb");
            Files.writeString(first, "one");
            Files.writeString(second, "two");

            PendingIndex index = new PendingIndex(indexDir, FIXED_CLOCK);
            index.remove(first);

            List<PendingIndexEntry<String>> entries = index.list(IDENTITY);
            assertEquals(1, entries.size());
            assertEquals("two", entries.getFirst().parsed());
        }
    }

    @Nested
    class FilenameGeneration {

        @Test
        void generateProducesSortableFilename() {
            String filename = PendingIndexFilename.generate(FIXED_CLOCK, "aaaaaaaa");

            assertTrue(filename.startsWith("20260220153045123_"));
        }

        @Test
        void generateProducesUniqueFilenamesForDistinctIds() {
            String first = PendingIndexFilename.generate(FIXED_CLOCK, "aaaaaaaa");
            String second = PendingIndexFilename.generate(FIXED_CLOCK, "bbbbbbbb");

            assertNotEquals(first, second);
        }
    }
}
