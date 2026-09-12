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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileSystemUtilsTest {

    @Nested
    class Size {

        @TempDir
        Path dir;

        @Test
        void fileWithContentReportsItsLength() throws IOException {
            byte[] content = "gc log line\n".repeat(100).getBytes(StandardCharsets.UTF_8);
            Path file = Files.write(dir.resolve("gc-jvm.log"), content);

            assertEquals(content.length, FileSystemUtils.size(file));
        }

        @Test
        void emptyFileReportsZero() throws IOException {
            Path file = Files.createFile(dir.resolve("profile-20260912-055901.jfr"));

            assertEquals(0L, FileSystemUtils.size(file));
        }

        @Test
        void missingFileFails() {
            Path missing = dir.resolve("nothing-here.jfr");

            assertThrows(RuntimeException.class, () -> FileSystemUtils.size(missing));
        }
    }
}
