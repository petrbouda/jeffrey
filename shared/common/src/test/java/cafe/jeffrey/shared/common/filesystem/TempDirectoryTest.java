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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What lands in a temp directory is often named by another machine; the directory takes a
 * plain file name and nothing else.
 */
class TempDirectoryTest {

    @TempDir
    Path root;

    @Test
    void resolvesAPlainNameInsideItself() {
        try (TempDirectory dir = new TempDirectory(root.resolve("download"))) {
            assertEquals(root.resolve("download").resolve("gc.jvm-log"), dir.resolve("gc.jvm-log"));
            assertEquals(root.resolve("download").resolve("profile-1.jfr.lz4"), dir.resolve("profile-1.jfr.lz4"));
        }
    }

    @Test
    void refusesANameThatWouldLeaveOrNestBelowIt() {
        try (TempDirectory dir = new TempDirectory(root.resolve("download"))) {
            assertThrows(IllegalArgumentException.class, () -> dir.resolve("../escape.log"));
            assertThrows(IllegalArgumentException.class, () -> dir.resolve("sub/nested.log"));
            assertThrows(IllegalArgumentException.class, () -> dir.resolve(root.resolve("elsewhere.log").toString()));
            assertThrows(IllegalArgumentException.class, () -> dir.resolve(".."));
            assertThrows(IllegalArgumentException.class, () -> dir.resolve("."));
            assertThrows(IllegalArgumentException.class, () -> dir.resolve(""));
        }
    }

    @Test
    void isCreatedOnOpenAndRemovedOnClose() {
        Path path = root.resolve("download");
        try (TempDirectory dir = new TempDirectory(path)) {
            assertTrue(Files.isDirectory(dir.path()));
        }
        assertFalse(Files.exists(path));
    }
}
