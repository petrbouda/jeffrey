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

package pbouda.jeffrey.shared.folderqueue;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FolderQueueTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-02-20T15:30:45.123Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    @Nested
    class Publish {

        @Test
        void writesFileWithContent() {
            Path queueDir = tempDir.resolve(".events");
            FolderQueue queue = new FolderQueue(queueDir, FIXED_CLOCK);

            queue.publish("my-event-id", "{\"eventType\":\"PROJECT_CREATED\"}");

            assertTrue(Files.isDirectory(queueDir));
            List<Path> files = listFiles(queueDir);
            assertEquals(1, files.size());

            String filename = files.getFirst().getFileName().toString();
            assertEquals("20260220153045123_my-event-id.json", filename);
        }

        @Test
        void createsQueueDirectoryIfMissing() {
            Path queueDir = tempDir.resolve("nested").resolve(".events");
            FolderQueue queue = new FolderQueue(queueDir, FIXED_CLOCK);

            queue.publish("test-id", "content");

            assertTrue(Files.isDirectory(queueDir));
            assertEquals(1, listFiles(queueDir).size());
        }

        @Test
        void writesCorrectContent() throws IOException {
            Path queueDir = tempDir.resolve(".events");
            String expected = "{\"key\":\"value\"}";
            FolderQueue queue = new FolderQueue(queueDir, FIXED_CLOCK);

            queue.publish("content-id", expected);

            Path file = listFiles(queueDir).getFirst();
            assertEquals(expected, Files.readString(file));
        }
    }

    @Nested
    class Poll {

        @Test
        void returnsSortedEntries() throws IOException {
            Path queueDir = tempDir.resolve(".events");
            Files.createDirectories(queueDir);
            Files.writeString(queueDir.resolve("20260220153045200_aaaaaaaa.json"), "second");
            Files.writeString(queueDir.resolve("20260220153045100_bbbbbbbb.json"), "first");
            Files.writeString(queueDir.resolve("20260220153045300_cccccccc.json"), "third");

            FolderQueue queue = new FolderQueue(queueDir, FIXED_CLOCK);
            List<FolderQueueEntry<String>> entries = queue.poll((path, content) -> Optional.of(content));

            assertEquals(3, entries.size());
            assertEquals("first", entries.get(0).parsed());
            assertEquals("second", entries.get(1).parsed());
            assertEquals("third", entries.get(2).parsed());
        }

        @Test
        void skipsFilesWhenParserReturnsEmpty() throws IOException {
            Path queueDir = tempDir.resolve(".events");
            Files.createDirectories(queueDir);
            Files.writeString(queueDir.resolve("20260220153045100_aaaaaaaa.json"), "valid");
            Files.writeString(queueDir.resolve("20260220153045200_bbbbbbbb.json"), "invalid");

            FolderQueue queue = new FolderQueue(queueDir, FIXED_CLOCK);
            List<FolderQueueEntry<String>> entries = queue.poll(
                    (path, content) -> "valid".equals(content) ? Optional.of(content) : Optional.empty());

            assertEquals(1, entries.size());
            assertEquals("valid", entries.getFirst().parsed());
        }

        @Test
        void skipsProcessedDirectory() throws IOException {
            Path queueDir = tempDir.resolve(".events");
            Files.createDirectories(queueDir.resolve(".processed"));
            Files.writeString(queueDir.resolve("20260220153045100_aaaaaaaa.json"), "pending");
            Files.writeString(queueDir.resolve(".processed").resolve("20260220153045000_dddddddd.json"), "processed");

            FolderQueue queue = new FolderQueue(queueDir, FIXED_CLOCK);
            List<FolderQueueEntry<String>> entries = queue.poll((path, content) -> Optional.of(content));

            assertEquals(1, entries.size());
            assertEquals("pending", entries.getFirst().parsed());
        }

        @Test
        void skipsHiddenFiles() throws IOException {
            Path queueDir = tempDir.resolve(".events");
            Files.createDirectories(queueDir);
            Files.writeString(queueDir.resolve("20260220153045100_aaaaaaaa.json"), "visible");
            Files.writeString(queueDir.resolve(".hidden-file.json"), "hidden");

            FolderQueue queue = new FolderQueue(queueDir, FIXED_CLOCK);
            List<FolderQueueEntry<String>> entries = queue.poll((path, content) -> Optional.of(content));

            assertEquals(1, entries.size());
            assertEquals("visible", entries.getFirst().parsed());
        }

        @Test
        void returnsEmptyForNonExistentDirectory() {
            Path queueDir = tempDir.resolve("nonexistent");
            FolderQueue queue = new FolderQueue(queueDir, FIXED_CLOCK);

            List<FolderQueueEntry<String>> entries = queue.poll((path, content) -> Optional.of(content));

            assertTrue(entries.isEmpty());
        }

        @Test
        void entryContainsCorrectFilenameAndPath() throws IOException {
            Path queueDir = tempDir.resolve(".events");
            Files.createDirectories(queueDir);
            String filename = "20260220153045100_aaaaaaaa.json";
            Files.writeString(queueDir.resolve(filename), "content");

            FolderQueue queue = new FolderQueue(queueDir, FIXED_CLOCK);
            List<FolderQueueEntry<String>> entries = queue.poll((path, content) -> Optional.of(content));

            assertEquals(1, entries.size());
            assertEquals(filename, entries.getFirst().filename());
            assertEquals(queueDir.resolve(filename), entries.getFirst().filePath());
        }
    }

    @Nested
    class Acknowledge {

        @Test
        void movesFileToProcessedDirectory() throws IOException {
            Path queueDir = tempDir.resolve(".events");
            Files.createDirectories(queueDir);
            Path eventFile = queueDir.resolve("20260220153045100_aaaaaaaa.json");
            Files.writeString(eventFile, "content");

            FolderQueue queue = new FolderQueue(queueDir, FIXED_CLOCK);
            queue.acknowledge(eventFile);

            assertFalse(Files.exists(eventFile));
            Path processedFile = queueDir.resolve(".processed").resolve("20260220153045100_aaaaaaaa.json");
            assertTrue(Files.exists(processedFile));
            assertEquals("content", Files.readString(processedFile));
        }

        @Test
        void createsProcessedDirectoryIfMissing() throws IOException {
            Path queueDir = tempDir.resolve(".events");
            Files.createDirectories(queueDir);
            Path eventFile = queueDir.resolve("20260220153045100_aaaaaaaa.json");
            Files.writeString(eventFile, "content");

            FolderQueue queue = new FolderQueue(queueDir, FIXED_CLOCK);
            queue.acknowledge(eventFile);

            assertTrue(Files.isDirectory(queueDir.resolve(".processed")));
        }
    }

    @Nested
    class Cleanup {

        @Test
        void deletesFilesOlderThanRetention() throws IOException {
            Path queueDir = tempDir.resolve(".events");
            Path processedDir = queueDir.resolve(".processed");
            Files.createDirectories(processedDir);

            // Old file: 2026-02-12 (8 days before FIXED_INSTANT, clearly older than 7-day retention)
            Files.writeString(processedDir.resolve("20260212153045123_aaaaaaaa.json"), "old");
            // Recent file: 2026-02-20 (same day as FIXED_INSTANT)
            Files.writeString(processedDir.resolve("20260220143045123_bbbbbbbb.json"), "recent");

            FolderQueue queue = new FolderQueue(queueDir, FIXED_CLOCK);
            queue.cleanup(Duration.ofDays(7));

            assertFalse(Files.exists(processedDir.resolve("20260212153045123_aaaaaaaa.json")));
            assertTrue(Files.exists(processedDir.resolve("20260220143045123_bbbbbbbb.json")));
        }

        @Test
        void handlesEmptyProcessedDirectory() throws IOException {
            Path queueDir = tempDir.resolve(".events");
            Path processedDir = queueDir.resolve(".processed");
            Files.createDirectories(processedDir);

            FolderQueue queue = new FolderQueue(queueDir, FIXED_CLOCK);
            assertDoesNotThrow(() -> queue.cleanup(Duration.ofDays(7)));
        }

        @Test
        void handlesNonExistentProcessedDirectory() {
            Path queueDir = tempDir.resolve(".events");
            FolderQueue queue = new FolderQueue(queueDir, FIXED_CLOCK);

            assertDoesNotThrow(() -> queue.cleanup(Duration.ofDays(7)));
        }
    }

    @Nested
    class FilenameGeneration {

        @Test
        void generateProducesSortableFilename() {
            String filename = FolderQueueFilename.generate(FIXED_CLOCK, "my-id");

            assertEquals("20260220153045123_my-id.json", filename);
        }

        @Test
        void generateProducesUniqueFilenames() {
            // Different instants produce different filenames
            Clock clock1 = Clock.fixed(Instant.parse("2026-02-20T15:30:45.100Z"), ZoneOffset.UTC);
            Clock clock2 = Clock.fixed(Instant.parse("2026-02-20T15:30:45.200Z"), ZoneOffset.UTC);
            String f1 = FolderQueueFilename.generate(clock1, "id-1");
            String f2 = FolderQueueFilename.generate(clock2, "id-2");

            assertNotEquals(f1, f2);
        }

        @Test
        void parseTimestampExtractsCorrectInstant() {
            Instant parsed = FolderQueueFilename.parseTimestamp("20260220153045123_019505a1.json");

            assertEquals(FIXED_INSTANT, parsed);
        }

        @Test
        void generateAndParseRoundTrip() {
            String filename = FolderQueueFilename.generate(FIXED_CLOCK, "round-trip-id");
            Instant parsed = FolderQueueFilename.parseTimestamp(filename);

            assertEquals(FIXED_INSTANT, parsed);
        }
    }

    private static List<Path> listFiles(Path dir) {
        try (var stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile).toList();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
