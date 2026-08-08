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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HomeRedirectTest {

    @Test
    void withoutMarkerReturnsTheSameDirectory(@TempDir Path tempDir) {
        assertEquals(tempDir, HomeRedirect.follow(tempDir));
    }

    @Test
    void followsSingleRedirect(@TempDir Path tempDir) throws Exception {
        Path home = Files.createDirectories(tempDir.resolve("home"));
        Path target = Files.createDirectories(tempDir.resolve("target"));
        HomeRedirect.write(home, target);

        assertEquals(target, HomeRedirect.follow(home));
    }

    @Test
    void followsRedirectChains(@TempDir Path tempDir) throws Exception {
        Path first = Files.createDirectories(tempDir.resolve("first"));
        Path second = Files.createDirectories(tempDir.resolve("second"));
        Path third = Files.createDirectories(tempDir.resolve("third"));
        HomeRedirect.write(first, second);
        HomeRedirect.write(second, third);

        assertEquals(third, HomeRedirect.follow(first));
    }

    @Test
    void stopsOnCycle(@TempDir Path tempDir) throws Exception {
        Path first = Files.createDirectories(tempDir.resolve("first"));
        Path second = Files.createDirectories(tempDir.resolve("second"));
        HomeRedirect.write(first, second);
        HomeRedirect.write(second, first);

        assertEquals(second, HomeRedirect.follow(first));
    }

    @Test
    void ignoresBlankMarker(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve(HomeRedirect.REDIRECT_FILE), "   ");

        assertEquals(tempDir, HomeRedirect.follow(tempDir));
    }

    @Test
    void ignoresRelativePathInMarker(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve(HomeRedirect.REDIRECT_FILE), "relative/path");

        assertEquals(tempDir, HomeRedirect.follow(tempDir));
    }
}
