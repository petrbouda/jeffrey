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
            Path file = Files.write(dir.resolve("gc.jvm-log"), content);

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
