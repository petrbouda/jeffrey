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

package cafe.jeffrey.storage.recording.filesystem;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A recording folder holds the files a profile is parsed from — one for an upload, one per chunk
 * for a downloaded session — and whatever came beside them.
 */
class FilesystemProjectRecordingStorageTest {

    private static final String RECORDING_ID = "rec-1";

    @TempDir
    Path projectFolder;

    private FilesystemProjectRecordingStorage storage;
    private Path recordingFolder;

    @BeforeEach
    void setUp() throws IOException {
        storage = new FilesystemProjectRecordingStorage(projectFolder);
        recordingFolder = Files.createDirectories(projectFolder.resolve(RECORDING_ID));
    }

    private Path write(String name) throws IOException {
        return Files.writeString(recordingFolder.resolve(name), name);
    }

    @Test
    void listsTheRecordingFilesInReadingOrderAndTheRestAsAdditional() throws IOException {
        Path third = write("profile-20260301-140000.jfr");
        Path first = write("profile-20260301-120000.jfr.lz4");
        Path second = write("profile-20260301-130000.jfr");
        Path dump = write("heap.hprof");
        Path log = write("gc.jvm-log");

        assertEquals(List.of(first, second, third), storage.findRecordingFiles(RECORDING_ID));
        assertEquals(List.of(log, dump), storage.findAdditionalFiles(RECORDING_ID).stream().sorted().toList());
    }

    @Test
    void aRecordingFolderMayTakeMoreThanOneRecordingFile() throws IOException {
        write("profile-20260301-120000.jfr");

        Path target = storage.uploadTarget(RECORDING_ID, "profile-20260301-130000.jfr");

        assertEquals(recordingFolder.resolve("profile-20260301-130000.jfr"), target);
    }

    @Test
    void doesNotDeleteARecordingFileAsAnAdditionalOne() throws IOException {
        Path chunk = write("profile-20260301-120000.jfr");
        Path log = write("gc.jvm-log");

        storage.deleteAdditionalFile(RECORDING_ID, "profile-20260301-120000.jfr");
        storage.deleteAdditionalFile(RECORDING_ID, "gc.jvm-log");

        assertTrue(Files.exists(chunk));
        assertFalse(Files.exists(log));
    }
}
