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

package cafe.jeffrey.microscope.core.recovery;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoveryActionsTest {

    @Nested
    class StartFresh {

        @Test
        void removesAllHomeDirectoryContent(@TempDir Path tempDir) throws Exception {
            Path home = Files.createDirectories(tempDir.resolve("home"));
            Files.writeString(home.resolve("jeffrey-data.db"), "old data");
            Files.createDirectories(home.resolve("profiles").resolve("abc"));

            Path result = RecoveryActions.startFresh(home);

            assertEquals(home, result);
            assertTrue(Files.isDirectory(home));
            try (var entries = Files.list(home)) {
                assertEquals(0, entries.count());
            }
        }
    }

    @Nested
    class SwitchHome {

        @Test
        void createsNewHomeAndWritesRedirect(@TempDir Path tempDir) throws Exception {
            Path currentHome = Files.createDirectories(tempDir.resolve("current"));
            Path newHome = tempDir.resolve("brand-new");

            Path result = RecoveryActions.switchHome(currentHome, newHome.toString());

            assertEquals(newHome, result);
            assertTrue(Files.isDirectory(newHome));
            assertEquals(newHome.toString(),
                    Files.readString(currentHome.resolve(HomeRedirect.REDIRECT_FILE)));
        }

        @Test
        void rejectsBlankPath(@TempDir Path tempDir) {
            assertThrows(IllegalArgumentException.class,
                    () -> RecoveryActions.switchHome(tempDir, "   "));
        }

        @Test
        void rejectsRelativePath(@TempDir Path tempDir) {
            assertThrows(IllegalArgumentException.class,
                    () -> RecoveryActions.switchHome(tempDir, "relative/home"));
        }

        @Test
        void rejectsTheCurrentHome(@TempDir Path tempDir) {
            assertThrows(IllegalArgumentException.class,
                    () -> RecoveryActions.switchHome(tempDir, tempDir.toString()));
        }

        @Test
        void rejectsPathInsideTheCurrentHome(@TempDir Path tempDir) {
            assertThrows(IllegalArgumentException.class,
                    () -> RecoveryActions.switchHome(tempDir, tempDir.resolve("nested").toString()));
        }

        @Test
        void rejectsExistingFile(@TempDir Path tempDir) throws Exception {
            Path file = Files.writeString(tempDir.resolve("occupied"), "file content");
            Path currentHome = Files.createDirectories(tempDir.resolve("current"));

            assertThrows(IllegalArgumentException.class,
                    () -> RecoveryActions.switchHome(currentHome, file.toString()));
        }
    }
}
