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

package cafe.jeffrey.microscope.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A fetched file's path is its only record, so a directory an earlier build wrote under the
 * old {@code artifacts} name is adopted under {@code files} rather than left where nothing
 * would look for it.
 */
class MicroscopeJeffreyDirsTest {

    @TempDir
    Path home;

    @Test
    void createsTheLayoutOnAFreshHome() {
        MicroscopeJeffreyDirs dirs = new MicroscopeJeffreyDirs(home);

        dirs.initialize();

        assertTrue(Files.isDirectory(dirs.profiles()));
        assertTrue(Files.isDirectory(dirs.recordings()));
        assertTrue(Files.isDirectory(dirs.files()));
        assertTrue(Files.isDirectory(dirs.temp()));
    }

    @Test
    void adoptsFetchedFileDirectoriesUnderTheirEarlierName() throws IOException {
        Path legacyHomeWide = Files.createDirectories(home.resolve("artifacts/hub/project/session"));
        Files.writeString(legacyHomeWide.resolve("hs-jvm-err.log"), "crash");
        Path legacyProfile = Files.createDirectories(home.resolve("profiles/p-1/artifacts"));
        Files.writeString(legacyProfile.resolve("gc.jvm-log"), "gc");
        MicroscopeJeffreyDirs dirs = new MicroscopeJeffreyDirs(home);

        dirs.initialize();

        assertEquals("crash", Files.readString(dirs.files().resolve("hub/project/session/hs-jvm-err.log")));
        assertEquals("gc", Files.readString(dirs.profileDir("p-1").resolve("files/gc.jvm-log")));
        assertFalse(Files.exists(home.resolve("artifacts")));
        assertFalse(Files.exists(home.resolve("profiles/p-1/artifacts")));
    }

    @Test
    void leavesAnEarlierDirectoryAloneWhenTheCurrentOneAlreadyExists() throws IOException {
        Files.createDirectories(home.resolve("artifacts"));
        Files.writeString(Files.createDirectories(home.resolve("files")).resolve("keep.log"), "current");
        MicroscopeJeffreyDirs dirs = new MicroscopeJeffreyDirs(home);

        dirs.initialize();

        assertEquals("current", Files.readString(dirs.files().resolve("keep.log")));
        assertTrue(Files.isDirectory(home.resolve("artifacts")));
    }
}
