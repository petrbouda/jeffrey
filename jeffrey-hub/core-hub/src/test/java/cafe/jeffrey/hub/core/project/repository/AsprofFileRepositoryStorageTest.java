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

package cafe.jeffrey.hub.core.project.repository;

import cafe.jeffrey.hub.core.project.repository.file.AsprofFileInfoProcessor;
import cafe.jeffrey.hub.persistence.api.ProjectRepositoryRepository;
import cafe.jeffrey.shared.common.filesystem.FileSizeReader;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.repository.RecordingStatus;
import cafe.jeffrey.shared.common.model.repository.RepositoryFile;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class AsprofFileRepositoryStorageTest {

    /**
     * Which chunk a live session is still writing. Everything downstream rests on this: the
     * compression job refuses to touch it, both retention jobs refuse to delete it, and
     * {@link AsprofFileRepositoryStorage#sizeReader} opens it rather than trusting the listing.
     */
    @Nested
    class OpenChunk {

        private static final Instant T0 = Instant.parse("2026-02-20T12:00:00Z");

        private static RepositoryFile recording(String name, Instant createdAt) {
            return new RepositoryFile(
                    name, name, createdAt, 1L, SupportedRecordingFile.JFR, RecordingStatus.FINISHED, null);
        }

        private static RepositoryFile artifact(String name, Instant createdAt) {
            return new RepositoryFile(
                    name, name, createdAt, 1L, SupportedRecordingFile.APP_LOG, RecordingStatus.FINISHED, null);
        }

        private static RecordingStatus statusOf(List<RepositoryFile> files, String name) {
            return files.stream()
                    .filter(file -> file.name().equals(name))
                    .findFirst()
                    .orElseThrow()
                    .status();
        }

        @Test
        void marksTheNewestRecordingOfALiveSession() {
            List<RepositoryFile> marked = AsprofFileRepositoryStorage.withOpenChunkMarked(
                    List.of(recording("c1", T0),
                            recording("c3", T0.plusSeconds(120)),
                            recording("c2", T0.plusSeconds(60))),
                    RecordingStatus.ACTIVE);

            assertEquals(RecordingStatus.ACTIVE, statusOf(marked, "c3"));
            assertEquals(RecordingStatus.FINISHED, statusOf(marked, "c1"));
            assertEquals(RecordingStatus.FINISHED, statusOf(marked, "c2"));
        }

        @Test
        void picksByTimestampRatherThanByPositionInTheListing() {
            // The listing arrives sorted by filename for presentation. A recording whose name the
            // processor does not recognise takes its timestamp from the filesystem, so the two
            // orders part company — and every other reader of these files goes by the timestamp.
            List<RepositoryFile> marked = AsprofFileRepositoryStorage.withOpenChunkMarked(
                    List.of(recording("zzz-oldest", T0),
                            recording("aaa-newest", T0.plusSeconds(60))),
                    RecordingStatus.ACTIVE);

            assertEquals(RecordingStatus.ACTIVE, statusOf(marked, "aaa-newest"));
            assertEquals(RecordingStatus.FINISHED, statusOf(marked, "zzz-oldest"));
        }

        @Test
        void neverMarksAnArtifactEvenWhenItIsTheNewestFile() {
            List<RepositoryFile> marked = AsprofFileRepositoryStorage.withOpenChunkMarked(
                    List.of(recording("c1", T0),
                            artifact("app.log", T0.plusSeconds(120))),
                    RecordingStatus.ACTIVE);

            assertEquals(RecordingStatus.ACTIVE, statusOf(marked, "c1"));
            assertEquals(RecordingStatus.FINISHED, statusOf(marked, "app.log"));
        }

        @Test
        void leavesAFinishedSessionAlone() {
            List<RepositoryFile> files = List.of(recording("c1", T0), recording("c2", T0.plusSeconds(60)));

            List<RepositoryFile> marked =
                    AsprofFileRepositoryStorage.withOpenChunkMarked(files, RecordingStatus.FINISHED);

            assertSame(files, marked, "a finished session has no open chunk and needs no new list");
        }

        @Test
        void leavesALiveSessionWithNoRecordingAlone() {
            List<RepositoryFile> files = List.of(artifact("app.log", T0));

            List<RepositoryFile> marked =
                    AsprofFileRepositoryStorage.withOpenChunkMarked(files, RecordingStatus.ACTIVE);

            assertSame(files, marked);
        }

        @Test
        void doesNotMutateTheFilesItWasGiven() {
            RepositoryFile chunk = recording("c1", T0);

            AsprofFileRepositoryStorage.withOpenChunkMarked(List.of(chunk), RecordingStatus.ACTIVE);

            assertEquals(RecordingStatus.FINISHED, chunk.status(),
                    "the listing hands out values; marking one must not reach back into it");
        }
    }

    /**
     * Which files cost a round trip to the share. Every open here is paid per file, per session,
     * on every listing of the project, so the cases that read from the listing are as much the
     * contract as the case that does not.
     */
    @Nested
    class SizeReader {

        @Test
        void opensTheRecordingOfASessionThatIsStillRecording() {
            assertSame(FileSizeReader.LIVE_FILE,
                    AsprofFileRepositoryStorage.sizeReader(RecordingStatus.ACTIVE, SupportedRecordingFile.JFR));
        }

        @Test
        void opensTheLogsAndCachesOfASessionThatIsStillRecording() {
            assertSame(FileSizeReader.LIVE_FILE,
                    AsprofFileRepositoryStorage.sizeReader(RecordingStatus.ACTIVE, SupportedRecordingFile.JVM_LOG));
            assertSame(FileSizeReader.LIVE_FILE,
                    AsprofFileRepositoryStorage.sizeReader(RecordingStatus.ACTIVE, SupportedRecordingFile.ASPROF_TEMP));
        }

        @Test
        void readsACompressedRecordingFromTheListingEvenWhileTheSessionRecords() {
            // This hub wrote and closed it, so no other client holds it open and the listing is
            // final. A long session accumulates one of these every chunk, and opening each would
            // grow the cost of a listing without bound.
            assertSame(FileSizeReader.FILE_ATTRIBUTES,
                    AsprofFileRepositoryStorage.sizeReader(RecordingStatus.ACTIVE, SupportedRecordingFile.JFR_LZ4));
        }

        @Test
        void readsEveryFileOfAFinishedSessionFromTheListing() {
            for (SupportedRecordingFile fileType : SupportedRecordingFile.values()) {
                assertSame(FileSizeReader.FILE_ATTRIBUTES,
                        AsprofFileRepositoryStorage.sizeReader(RecordingStatus.FINISHED, fileType),
                        "file type: " + fileType);
            }
        }
    }

    @Nested
    class Describe {

        private static final byte[] CONTENT = "gc log line\n".repeat(100).getBytes(StandardCharsets.UTF_8);

        @TempDir
        Path workspace;

        private AsprofFileRepositoryStorage storage() {
            return new AsprofFileRepositoryStorage(
                    mock(ProjectInfo.class),
                    workspace,
                    mock(ProjectRepositoryRepository.class),
                    new AsprofFileInfoProcessor());
        }

        private Path sessionDir() throws IOException {
            return Files.createDirectories(workspace.resolve("project/instance/session"));
        }

        @Test
        void reportsAFileThatIsThere() throws IOException {
            Path session = sessionDir();
            Path file = Files.write(session.resolve("gc.jvm-log"), CONTENT);

            RepositoryFile described = storage().describe(file, RecordingStatus.ACTIVE, workspace, session);

            assertNotNull(described);
            assertEquals("gc.jvm-log", described.name());
            assertEquals(CONTENT.length, described.size());
            assertEquals(SupportedRecordingFile.JVM_LOG, described.fileType());
        }

        @Test
        void leavesOutAFileThatWentAwayWhileTheSessionWasListed() throws IOException {
            // async-profiler deletes its cache file as soon as it flushes a chunk, so a name the
            // listing has just read can be gone before its size is asked for. The next listing
            // will not mention it either; failing here would take the instance page down with it.
            Path session = sessionDir();
            Path vanished = session.resolve("profile-20260912-121559.jfr.1~");

            assertNull(storage().describe(vanished, RecordingStatus.ACTIVE, workspace, session));
        }

        @Test
        void leavesOutAVanishedFileOfAFinishedSessionToo() throws IOException {
            Path session = sessionDir();
            Path vanished = session.resolve("profile-20260912-121559.jfr");

            assertNull(storage().describe(vanished, RecordingStatus.FINISHED, workspace, session));
        }
    }
}
