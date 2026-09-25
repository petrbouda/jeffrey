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

package cafe.jeffrey.hub.core.grpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cafe.jeffrey.hub.api.v1.*;
import cafe.jeffrey.hub.core.manager.RepositoryManager;
import cafe.jeffrey.hub.core.project.repository.FileVanishedException;
import cafe.jeffrey.hub.persistence.api.SessionWithRepository;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
import cafe.jeffrey.hub.persistence.api.ProjectRepository;
import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.hub.model.repository.RecordingSession;
import cafe.jeffrey.hub.model.repository.RecordingStatus;
import cafe.jeffrey.hub.model.repository.RepositoryFile;
import cafe.jeffrey.hub.model.repository.StreamedFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileDownloadGrpcServiceTest {

    private static final String PROJECT_ID = "proj-1";
    private static final String SESSION_ID = "session-1";
    private static final String FILE_ID = "file-1";

    private InProcessGrpcServer grpc;

    private FileDownloadServiceGrpc.FileDownloadServiceStub startServer(
            FileDownloadGrpcService service) {
        grpc = InProcessGrpcServer.start(service);
        return FileDownloadServiceGrpc.newStub(grpc.channel());
    }

    @AfterEach
    void shutdown() {
        if (grpc != null) {
            grpc.close();
        }
    }


    /**
     * One RPC for every kind of file. There were two — one for recordings, one for artifacts —
     * and they differed only in the word in their refusal, so the pair of near-identical nested
     * classes that used to be here are one too.
     */
    @Nested
    class DownloadFile {

        @Test
        void sessionNotFound_returnsNotFound() throws Exception {
            var stub = startServer(serviceWithNoSession());
            var observer = new TestStreamObserver();

            stub.downloadFile(request("non-existent", FILE_ID), observer);

            assertTrue(observer.errorLatch.await(5, TimeUnit.SECONDS));
            assertStatus(Status.Code.NOT_FOUND, observer.error);
        }

        @Test
        void streamsARecordingChunk(@TempDir Path tempDir) throws Exception {
            assertStreams(tempDir, "recording.jfr", "JFR recording file content for testing");
        }

        /**
         * The same call, an artifact rather than a chunk. What the kind decides is what the
         * reader does with the bytes, not which door they come through.
         */
        @Test
        void streamsAnArtifactThroughTheSameCall(@TempDir Path tempDir) throws Exception {
            assertStreams(tempDir, "heapdump.hprof", "heap dump artifact content");
        }

        /**
         * A file with no bytes still has to say what it is. Without the header chunk the reader
         * is handed a stream with no name and no size, and has to fall back on what it asked
         * for — which is the very thing the name on the wire exists to replace.
         */
        @Test
        void anEmptyFileStillArrivesAsOneChunkThatNamesIt(@TempDir Path tempDir) throws Exception {
            Path empty = Files.createFile(tempDir.resolve("service-app.log"));

            var repoManager = mock(RepositoryManager.class);
            when(repoManager.streamFile(SESSION_ID, FILE_ID))
                    .thenReturn(new StreamedFile("service-app.log", empty));

            var stub = startServer(serviceWithSession(repoManager));
            var observer = new TestStreamObserver();

            stub.downloadFile(request(SESSION_ID, FILE_ID), observer);

            assertTrue(observer.completeLatch.await(5, TimeUnit.SECONDS));
            assertNull(observer.error, "Stream should complete without error");
            assertEquals(1, observer.chunks.size(), "an empty file is exactly one header chunk");
            assertEquals("service-app.log", observer.chunks.getFirst().getFilename());
            assertEquals(0, observer.chunks.getFirst().getTotalSize());
            assertEquals(0, observer.chunks.getFirst().getData().size());
        }

        private void assertStreams(Path tempDir, String filename, String body) throws Exception {
            byte[] content = body.getBytes();
            Path tempFile = Files.write(tempDir.resolve(filename), content);

            var repoManager = mock(RepositoryManager.class);
            when(repoManager.streamFile(SESSION_ID, FILE_ID))
                    .thenReturn(new StreamedFile(filename, tempFile));

            var stub = startServer(serviceWithSession(repoManager));
            var observer = new TestStreamObserver();

            stub.downloadFile(request(SESSION_ID, FILE_ID), observer);

            assertTrue(observer.completeLatch.await(5, TimeUnit.SECONDS));
            assertNull(observer.error, "Stream should complete without error");
            assertFalse(observer.chunks.isEmpty(), "Should receive at least one chunk");

            assertTotalSizeOnFirstChunkOnly(observer.chunks, content.length);
            assertEquals(filename, observer.chunks.getFirst().getFilename(),
                    "the receiver is told the name the file has here, not the one it asked for");
            assertArrayEquals(content, reassemble(observer.chunks));
        }

        /**
         * What the lookup refuses is a statement about what was asked for, not a hub failure.
         * Reported as INTERNAL it reached the caller as {@code REMOTE_OPERATION_FAILED} — the
         * same thing an unreachable hub gives — with a stack trace in the hub's log for every
         * mistyped id.
         */
        @Test
        void aRefusalIsReportedAsAnInvalidArgument() throws Exception {
            var repoManager = mock(RepositoryManager.class);
            when(repoManager.streamFile(SESSION_ID, FILE_ID))
                    .thenThrow(new IllegalArgumentException("Recording profile-1.jfr is empty"));

            var stub = startServer(serviceWithSession(repoManager));
            var observer = new TestStreamObserver();

            stub.downloadFile(request(SESSION_ID, FILE_ID), observer);

            assertTrue(observer.errorLatch.await(5, TimeUnit.SECONDS));
            assertStatus(Status.Code.INVALID_ARGUMENT, observer.error);
            assertEquals("Recording profile-1.jfr is empty",
                    Status.fromThrowable(observer.error).getDescription(),
                    "and the reason travels with it, since it is the whole use of refusing by reason");
        }

        /**
         * The compression job publishes the archive and removes the recording between the
         * listing and the open. The id survives that rewrite, so the same id is resolved once
         * more — and what comes back is the archive, under the archive's name.
         */
        @Test
        void resolvesTheIdAgainWhenTheFileWasReplacedUnderIt(@TempDir Path tempDir) throws Exception {
            byte[] archived = "the archive of that chunk".getBytes();
            Path archive = Files.write(tempDir.resolve("profile-1.jfr.lz4"), archived);

            var repoManager = mock(RepositoryManager.class);
            when(repoManager.streamFile(SESSION_ID, FILE_ID))
                    .thenThrow(new FileVanishedException("File profile-1.jfr was listed but is no longer on disk"))
                    .thenReturn(new StreamedFile("profile-1.jfr.lz4", archive));

            var stub = startServer(serviceWithSession(repoManager));
            var observer = new TestStreamObserver();

            stub.downloadFile(request(SESSION_ID, FILE_ID), observer);

            assertTrue(observer.completeLatch.await(5, TimeUnit.SECONDS));
            assertNull(observer.error, "the retry is invisible to the caller");
            assertEquals("profile-1.jfr.lz4", observer.chunks.getFirst().getFilename());
            assertArrayEquals(archived, reassemble(observer.chunks));
        }

        /**
         * The same race caught one step later: the listing still named the recording, and it was
         * gone by the time the file was opened. Nothing has been sent at that point, so the
         * second answer can still be streamed in full.
         */
        @Test
        void resolvesTheIdAgainWhenTheFileIsGoneByTheTimeItIsOpened(@TempDir Path tempDir) throws Exception {
            byte[] archived = "the archive of that chunk".getBytes();
            Path archive = Files.write(tempDir.resolve("profile-1.jfr.lz4"), archived);
            Path removed = tempDir.resolve("profile-1.jfr");

            var repoManager = mock(RepositoryManager.class);
            when(repoManager.streamFile(SESSION_ID, FILE_ID))
                    .thenReturn(new StreamedFile("profile-1.jfr", removed))
                    .thenReturn(new StreamedFile("profile-1.jfr.lz4", archive));

            var stub = startServer(serviceWithSession(repoManager));
            var observer = new TestStreamObserver();

            stub.downloadFile(request(SESSION_ID, FILE_ID), observer);

            assertTrue(observer.completeLatch.await(5, TimeUnit.SECONDS));
            assertNull(observer.error);
            assertEquals("profile-1.jfr.lz4", observer.chunks.getFirst().getFilename());
            assertArrayEquals(archived, reassemble(observer.chunks));
        }

        /**
         * Once, and once only. A second miss is not a rewrite in flight but a file that is
         * genuinely not there, and retrying it again would turn one missing file into a loop.
         */
        @Test
        void butOnlyOnce() throws Exception {
            var repoManager = mock(RepositoryManager.class);
            when(repoManager.streamFile(SESSION_ID, FILE_ID))
                    .thenThrow(new FileVanishedException("File profile-1.jfr is no longer on disk"));

            var stub = startServer(serviceWithSession(repoManager));
            var observer = new TestStreamObserver();

            stub.downloadFile(request(SESSION_ID, FILE_ID), observer);

            assertTrue(observer.errorLatch.await(5, TimeUnit.SECONDS));
            assertStatus(Status.Code.INVALID_ARGUMENT, observer.error);
        }

        private DownloadFileRequest request(String sessionId, String fileId) {
            return DownloadFileRequest.newBuilder()
                    .setSessionId(sessionId)
                    .setFileId(fileId)
                    .build();
        }
    }

    // ========== Helpers ==========

    private static final ProjectInfo TEST_PROJECT_INFO =
            new ProjectInfo(PROJECT_ID, null, null, null, null, null, null, null, null);

    /**
     * Creates a service where {@code repositoryManagerForSession(SESSION_ID)} succeeds.
     */
    private static final Instant SESSION_START = Instant.parse("2026-03-01T12:00:00Z");
    private static final Instant SESSION_END = SESSION_START.plusSeconds(1800);

    private static RepositoryFile chunk(String id, long startMinute) {
        return new RepositoryFile(id, "profile-" + id + ".jfr", SESSION_START.plusSeconds(startMinute * 60),
                10L, true, null);
    }

    /** Three ten-minute chunks, f1 f2 f3, the session finished at +30. */
    private static RecordingSession threeChunks() {
        return new RecordingSession("session-1", "session-1", "inst-1", SESSION_START, SESSION_END,
                RecordingStatus.FINISHED,
                List.of(chunk("f1", 0), chunk("f2", 10), chunk("f3", 20)), false);
    }

    private FileDownloadGrpcService serviceWithSession(RepositoryManager repoManager) {
        when(repoManager.findRecordingSessions(SESSION_ID)).thenReturn(Optional.of(threeChunks()));

        // The session names its project by id; the lookup then resolves the project itself
        var sessionWithRepo = mock(SessionWithRepository.class);
        when(sessionWithRepo.projectId()).thenReturn(PROJECT_ID);
        var projectRepo = mock(ProjectRepository.class);
        when(projectRepo.find()).thenReturn(Optional.of(TEST_PROJECT_INFO));

        var platformRepositories = mock(HubPlatformRepositories.class);
        when(platformRepositories.findSessionWithRepositoryById(SESSION_ID)).thenReturn(Optional.of(sessionWithRepo));
        when(platformRepositories.newProjectRepository(PROJECT_ID)).thenReturn(projectRepo);

        var repoManagerFactory = mock(RepositoryManager.Factory.class);
        when(repoManagerFactory.apply(TEST_PROJECT_INFO)).thenReturn(repoManager);

        return new FileDownloadGrpcService(new GrpcLookups(platformRepositories, repoManagerFactory, null), Runnable::run);
    }

    /**
     * Creates a service where {@code findSessionWithRepositoryById("non-existent")} returns empty.
     */
    private FileDownloadGrpcService serviceWithNoSession() {
        var platformRepositories = mock(HubPlatformRepositories.class);
        when(platformRepositories.findSessionWithRepositoryById("non-existent")).thenReturn(Optional.empty());

        var repoManagerFactory = mock(RepositoryManager.Factory.class);

        return new FileDownloadGrpcService(new GrpcLookups(platformRepositories, repoManagerFactory, null), Runnable::run);
    }

    private static void assertStatus(Status.Code expected, Throwable error) {
        assertNotNull(error, "Expected an error");
        assertInstanceOf(StatusRuntimeException.class, error);
        assertEquals(expected, ((StatusRuntimeException) error).getStatus().getCode());
    }

    /**
     * Verifies the first chunk carries {@code totalSize} and subsequent chunks omit it (default 0).
     * Reflects the wire-efficient pattern in {@code streamWithBackpressure}.
     */
    private static void assertTotalSizeOnFirstChunkOnly(List<DataChunk> chunks, int expectedTotalSize) {
        assertEquals(expectedTotalSize, chunks.get(0).getTotalSize(),
                "First chunk should carry the total size");
        for (int i = 1; i < chunks.size(); i++) {
            assertEquals(0L, chunks.get(i).getTotalSize(),
                    "Subsequent chunks should not repeat the total size: index=" + i);
        }
    }

    private static byte[] reassemble(List<DataChunk> chunks) throws IOException {
        ByteArrayOutputStream assembled = new ByteArrayOutputStream();
        for (DataChunk chunk : chunks) {
            chunk.getData().writeTo(assembled);
        }
        return assembled.toByteArray();
    }

    private static class TestStreamObserver implements StreamObserver<DataChunk> {

        final List<DataChunk> chunks = Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch completeLatch = new CountDownLatch(1);
        final CountDownLatch errorLatch = new CountDownLatch(1);
        volatile Throwable error;

        @Override
        public void onNext(DataChunk value) {
            chunks.add(value);
        }

        @Override
        public void onError(Throwable t) {
            error = t;
            errorLatch.countDown();
        }

        @Override
        public void onCompleted() {
            completeLatch.countDown();
        }
    }
}
