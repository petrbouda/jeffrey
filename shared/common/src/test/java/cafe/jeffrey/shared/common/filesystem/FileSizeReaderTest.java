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

package cafe.jeffrey.shared.common.filesystem;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileSizeReaderTest {

    private static final byte[] CONTENT = "gc log line\n".repeat(100).getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path dir;

    @Nested
    class OpenHandle {

        @Test
        void reportsTheLengthOfAFileWithContent() throws IOException {
            Path file = Files.write(dir.resolve("gc-jvm.log"), CONTENT);

            assertEquals(CONTENT.length, FileSizeReader.OPEN_HANDLE.size(file));
        }

        @Test
        void reportsZeroForAnEmptyFile() throws IOException {
            Path file = Files.createFile(dir.resolve("profile-20260912-055901.jfr"));

            assertEquals(0L, FileSizeReader.OPEN_HANDLE.size(file));
        }

        @Test
        void failsForAMissingFile() {
            assertThrows(RuntimeException.class, () -> FileSizeReader.OPEN_HANDLE.size(dir.resolve("missing")));
        }
    }

    @Nested
    class CachedAttributes {

        @Test
        void trustsANonZeroAttributeWithoutReReading() throws IOException {
            Path file = Files.write(dir.resolve("gc-jvm.log"), CONTENT);
            List<Path> reReads = new ArrayList<>();
            FileSizeReader reader = new FileSizeReader.CachedAttributes(path -> {
                reReads.add(path);
                return -1L;
            });

            assertEquals(CONTENT.length, reader.size(file));
            assertEquals(List.of(), reReads);
        }

        @Test
        void reReadsAZeroThroughTheGivenReader() throws IOException {
            Path file = Files.createFile(dir.resolve("profile-20260912-055901.jfr"));
            FileSizeReader reader = new FileSizeReader.CachedAttributes(_ -> 70_399L);

            assertEquals(70_399L, reader.size(file));
        }

        @Test
        void reportsZeroWhenBothAgreeTheFileIsEmpty() throws IOException {
            Path file = Files.createFile(dir.resolve("profile-20260912-055901.jfr"));

            assertEquals(0L, FileSizeReader.CACHED_ATTRIBUTES.size(file));
        }

        @Test
        void failsForAMissingFile() {
            assertThrows(RuntimeException.class,
                    () -> FileSizeReader.CACHED_ATTRIBUTES.size(dir.resolve("missing")));
        }
    }
}
