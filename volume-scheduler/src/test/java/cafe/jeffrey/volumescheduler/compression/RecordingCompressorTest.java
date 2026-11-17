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

package cafe.jeffrey.volumescheduler.compression;

import cafe.jeffrey.shared.volume.FilesystemVolume;
import cafe.jeffrey.shared.volume.VolumeEntry;
import cafe.jeffrey.shared.volume.VolumePath;
import cafe.jeffrey.shared.volume.recording.RecordingFormat;
import cafe.jeffrey.test.volume.VolumeWriter;
import cafe.jeffrey.volumescheduler.volume.ProjectRef;
import cafe.jeffrey.volumescheduler.volume.ProjectVolumeFixture;
import cafe.jeffrey.volumescheduler.volume.RecordingSession;
import cafe.jeffrey.volumescheduler.volume.VolumeProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The only thing that rewrites a file on the volume: which recordings it replaces with their LZ4
 * archive, what the archive keeps of the recording, and that an archive under its own name is
 * always whole.
 */
class RecordingCompressorTest {

    private static final Instant T0 = Instant.parse("2026-02-20T12:00:00Z");
    private static final ProjectRef REF = new ProjectRef("ws", "project");
    private static final String SESSION_ID = VolumeWriter.sessionIdAt(T0);

    /** Long enough that LZ4 has something to do with it, short enough to read in a failure. */
    private static final String CHUNK_BODY = "a chunk of events, repeated so the frame compresses".repeat(20);

    private final RecordingCompressor compressor = new RecordingCompressor();

    @TempDir
    Path workspacesDir;

    private Path projectDir;
    private Path session;

    @BeforeEach
    void setUp() {
        projectDir = VolumeWriter.project(workspacesDir.resolve(REF.workspaceId()), REF.projectId());
        session = VolumeWriter.session(VolumeWriter.instance(projectDir, "instance"), T0);
    }

    private VolumeProject project() {
        return ProjectVolumeFixture.project(REF, workspacesDir, projectDir);
    }

    /** Ends the session: a newer one starts in its instance, so every recording of it is complete. */
    private void endTheSession() {
        VolumeWriter.session(VolumeWriter.instance(projectDir, "instance"), T0.plusSeconds(1800));
    }

    /** Compresses the session as the compression job reads it: listed, with its files. */
    private int compress() {
        RecordingSession listed = project().sessions().stream()
                .filter(candidate -> candidate.id().equals(SESSION_ID))
                .findFirst()
                .orElseThrow();
        return compressor.compress(listed);
    }

    private static Path write(Path dir, String name) throws IOException {
        return Files.write(dir.resolve(name), CHUNK_BODY.getBytes(StandardCharsets.UTF_8));
    }

    /** The session directory's files as a reader sees them: dot-files are the volume's own bookkeeping. */
    private static List<String> names(Path dir) throws IOException {
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.map(path -> path.getFileName().toString())
                    .filter(name -> !name.startsWith("."))
                    .sorted()
                    .toList();
        }
    }

    /** Whatever the compression left behind that is hidden, which is what a scratch name is. */
    private static List<String> scratchFiles(Path dir) throws IOException {
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("."))
                    .toList();
        }
    }

    @Nested
    class WhichRecordings {

        @Test
        void replacesEveryClosedChunkWithItsArchive() throws IOException {
            write(session, "rec-20260220-120000+0000.jfr");
            write(session, "rec-20260220-121000+0000.jfr");
            endTheSession();

            assertEquals(2, compress());

            assertEquals(
                    List.of("rec-20260220-120000+0000.jfr.lz4", "rec-20260220-121000+0000.jfr.lz4"),
                    names(session),
                    "each recording is gone and its archive is there");
            assertEquals(List.of(), scratchFiles(session), "the scratch file does not outlive the compression");
        }

        /**
         * Compressing a recording still being copied into place would compress a prefix of it and
         * then delete the file being written into. It is left out by {@code trimmableRecordings()},
         * which is the one definition of which recording that may be.
         */
        @Test
        void leavesTheNewestRecordingOfTheNewestSession() throws IOException {
            write(session, "rec-20260220-120000+0000.jfr");
            write(session, "rec-20260220-121000+0000.jfr");

            assertEquals(1, compress());

            assertEquals(
                    List.of("rec-20260220-120000+0000.jfr.lz4", "rec-20260220-121000+0000.jfr"),
                    names(session),
                    "the newest recording of the instance's newest session is untouched");
        }

        /**
         * Two instances of one project record at once, so only one of the two recording sessions
         * is the project's newest — and that must change nothing. Which session is the newest is a
         * fact of its own instance, not of its place in the project's listing. The older live
         * session once read as finished and had the chunk its profiler was writing compressed.
         */
        @Test
        void everyInstancesNewestSessionKeepsItsNewestRecordingWhateverItsPosition() throws IOException {
            write(session, "rec-20260220-120000+0000.jfr");
            write(session, "rec-20260220-121000+0000.jfr");
            Path newer = VolumeWriter.session(VolumeWriter.instance(projectDir, "instance-2"), T0.plusSeconds(1800));
            write(newer, "rec-20260220-123000+0000.jfr");

            List<RecordingSession> sessions = project().sessions();
            sessions.forEach(compressor::compress);

            assertEquals(List.of("rec-20260220-120000+0000.jfr.lz4", "rec-20260220-121000+0000.jfr"), names(session));
            assertEquals(List.of("rec-20260220-123000+0000.jfr"), names(newer),
                    "each instance's newest session holds back its own newest recording");
        }

        /**
         * {@code app.pprof.lz4} would read as no recording at all: it would change id and take the
         * compression's timestamp — with the original deleted. Only a JFR round-trips.
         */
        @Test
        void leavesAFileItsTypeCannotCompress() throws IOException {
            write(session, "app.pprof");
            write(session, "app.otlp");
            endTheSession();

            assertEquals(0, compress());

            assertEquals(List.of("app.otlp", "app.pprof"), names(session));
        }

        /**
         * A profiler stopped before it wrote an event leaves a zero-byte recording. Compressing one
         * would delete it in favour of an archive of nothing, and the emptiness is what a reader
         * that asks for the file is told.
         */
        @Test
        void leavesAnEmptyRecordingAlone() throws IOException {
            Files.createFile(session.resolve("rec-20260220-120000+0000.jfr"));
            endTheSession();

            assertEquals(0, compress());

            assertEquals(List.of("rec-20260220-120000+0000.jfr"), names(session));
        }

        /** A second run has nothing left to do and changes nothing. */
        @Test
        void isIdempotent() throws IOException {
            write(session, "rec-20260220-120000+0000.jfr");
            endTheSession();

            assertEquals(1, compress());
            long firstSize = Files.size(session.resolve("rec-20260220-120000+0000.jfr.lz4"));

            assertEquals(0, compress());

            assertEquals(List.of("rec-20260220-120000+0000.jfr.lz4"), names(session));
            assertEquals(firstSize, Files.size(session.resolve("rec-20260220-120000+0000.jfr.lz4")));
        }
    }

    @Nested
    class AnArchiveAlreadyThere {

        /**
         * The recording is deleted on the strength of an archive already being there, which is
         * only safe because an archive under its own name is whole: it is renamed onto that name
         * rather than written to it. Before the rename, a run killed mid-compression left a partial
         * archive beside a whole recording, and this shortcut deleted the recording.
         */
        @Test
        void deletesASourceLeftBesideItsOwnArchive() throws IOException {
            Path source = write(session, "rec-20260220-120000+0000.jfr");
            Path archive = write(session, "rec-20260220-120000+0000.jfr.lz4");
            Files.setLastModifiedTime(source, FileTime.from(T0));
            Files.setLastModifiedTime(archive, FileTime.from(T0.plusSeconds(5)));
            endTheSession();

            assertEquals(1, compress());

            assertEquals(List.of("rec-20260220-120000+0000.jfr.lz4"), names(session));
        }

        /**
         * Two chunks with one name: a template without the file number, through the hour that
         * repeats when daylight saving ends. The archive is the earlier chunk's, so the recording
         * beside it is newer than it, and deleting it as a leftover would delete that chunk.
         */
        @Test
        void keepsANewerChunkWhoseNameAnOlderArchiveHolds() throws IOException {
            Path archive = write(session, "rec-20260220-120000+0000.jfr.lz4");
            Path source = write(session, "rec-20260220-120000+0000.jfr");
            Files.setLastModifiedTime(archive, FileTime.from(T0));
            Files.setLastModifiedTime(source, FileTime.from(T0.plusSeconds(3600)));
            endTheSession();

            assertEquals(0, compress());

            assertEquals(List.of("rec-20260220-120000+0000.jfr", "rec-20260220-120000+0000.jfr.lz4"),
                    names(session), "both chunks stay");
        }
    }

    @Nested
    class WhatTheArchiveKeeps {

        /**
         * The three things a name decides — the type, the id, and when the file was opened — all
         * have to survive, or the archive is not the same file to any reader of it.
         */
        @Test
        void theArchiveIsStillTheSameRecording() throws IOException {
            Path source = write(session, "rec-20260220-120500+0000.jfr");
            VolumeEntry before = entry(source);
            endTheSession();

            compress();

            Path archive = session.resolve("rec-20260220-120500+0000.jfr.lz4");
            assertTrue(Files.size(archive) > 0);
            RecordingFormat archived = RecordingFormat.of(archive.getFileName().toString()).orElseThrow();
            assertEquals(RecordingFormat.JFR_LZ4, archived);
            assertTrue(archived.isArchive());
            assertEquals(
                    RecordingFormat.JFR.timestampResolver().resolve(before),
                    archived.timestampResolver().resolve(entry(archive)),
                    "the archive answers with the instant the profiler opened the chunk");
            assertEquals(
                    RecordingFormat.JFR.idOf(source.getFileName().toString()),
                    archived.idOf(archive.getFileName().toString()),
                    "and to the id a reader took from the listing before the rewrite");
        }

        /** The file as a reader of the volume rooted at its session directory describes it. */
        private VolumeEntry entry(Path file) {
            return new FilesystemVolume(file.getParent())
                    .stat(VolumePath.root().resolve(file.getFileName().toString()))
                    .orElseThrow();
        }
    }
}
