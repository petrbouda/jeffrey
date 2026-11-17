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

package cafe.jeffrey.profile.manager.heapdump;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class HeapDumpFilesTest {

    @TempDir
    Path profileDir;

    @Test
    void livesInTheProfilesHeapDumpDirectory() {
        assertEquals(profileDir.resolve("heap-dump"), HeapDumpFiles.inProfileDir(profileDir).analysisDir());
    }

    @Test
    void findsNoDumpInAProfileThatHasNone() {
        HeapDumpFiles files = HeapDumpFiles.inProfileDir(profileDir);

        assertEquals(Optional.empty(), files.dumpPath());
        assertFalse(files.dumpExists());
    }

    /** The reports and the index sit beside the dump; only the dump is the dump. */
    @Test
    void findsTheDumpAmongItsReports() throws IOException {
        HeapDumpFiles files = HeapDumpFiles.inProfileDir(profileDir);
        Files.createDirectories(files.analysisDir());
        Files.writeString(files.analysisDir().resolve("string-analysis.json"), "{}");
        Path dump = Files.writeString(files.analysisDir().resolve("app.hprof"), "");

        assertEquals(Optional.of(dump), files.dumpPath());
    }

    /** A decompressed gzipped dump leaves both files; the plain one is the one analysed. */
    @Test
    void prefersTheDecompressedDumpOverItsArchive() throws IOException {
        HeapDumpFiles files = HeapDumpFiles.inProfileDir(profileDir);
        Files.createDirectories(files.analysisDir());
        Files.writeString(files.analysisDir().resolve("app.hprof.gz"), "");
        Path plain = Files.writeString(files.analysisDir().resolve("app.hprof"), "");

        assertEquals(Optional.of(plain), files.dumpPath());
    }
}
