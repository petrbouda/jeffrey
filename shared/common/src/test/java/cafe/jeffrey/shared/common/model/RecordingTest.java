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

package cafe.jeffrey.shared.common.model;

import cafe.jeffrey.shared.common.model.repository.SupportedFile;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A recording is the files a profile is parsed from, in reading order, plus whatever came beside
 * them. A session downloaded from a hub holds one file per chunk; an upload holds one.
 */
class RecordingTest {

    private static final Instant NOW = Instant.parse("2026-03-01T12:00:00Z");

    private static RecordingFile file(String name, SupportedFile type, long size) {
        return new RecordingFile(name, "rec-1", name, type, NOW, size);
    }

    private static Recording recording(RecordingFile... files) {
        return new Recording("rec-1", "checkout_2026-03-01", null, null, RecordingEventSource.JDK,
                NOW, NOW, NOW, false, null, null, List.of(files));
    }

    @Test
    void ordersTheChunksByNameWhicheverFormEachIsIn() {
        RecordingFile third = file("profile-20260301-140000.jfr", SupportedFile.JFR, 30);
        RecordingFile first = file("profile-20260301-120000.jfr.lz4", SupportedFile.JFR_LZ4, 10);
        RecordingFile second = file("profile-20260301-130000.jfr", SupportedFile.JFR, 20);
        RecordingFile log = file("gc.jvm-log", SupportedFile.JVM_LOG, 5);

        Recording recording = recording(third, log, first, second);

        assertEquals(List.of(first, second, third), recording.recordingFiles());
        assertEquals(List.of(log), recording.additionalFiles());
        assertEquals(60, recording.recordingSizeInBytes());
    }

    @Test
    void anUploadIsARecordingWithOneFile() {
        RecordingFile upload = file("app.jfr", SupportedFile.JFR, 1024);
        RecordingFile dump = file("heap.hprof", SupportedFile.HEAP_DUMP, 4096);

        Recording recording = recording(upload, dump);

        assertEquals(List.of(upload), recording.recordingFiles());
        assertEquals(List.of(dump), recording.additionalFiles());
        assertEquals(1024, recording.recordingSizeInBytes());
    }

    /**
     * A heap dump is not parsed as a recording, so it is nobody's recording file; its size is
     * still the recording's, since it is all the recording holds.
     */
    @Test
    void aHeapDumpAloneHasNoRecordingFileButKeepsItsSize() {
        RecordingFile dump = file("heap.hprof", SupportedFile.HEAP_DUMP, 4096);

        Recording recording = recording(dump);

        assertTrue(recording.recordingFiles().isEmpty());
        assertEquals(4096, recording.recordingSizeInBytes());
    }

    @Test
    void pprofAndOtlpFilesAreRecordingFilesToo() {
        RecordingFile pprof = file("cpu.pprof", SupportedFile.PPROF, 100);

        assertEquals(List.of(pprof), recording(pprof).recordingFiles());
    }
}
