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
import cafe.jeffrey.hub.persistence.api.SessionWithRepository;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.repository.StreamedFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
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
     * One RPC for every file a session holds: a JFR chunk, a heap dump and anything else stream
     * the same way, as they lie on the hub. What the manager refuses arrives as a status the
     * client can act on rather than as {@code INTERNAL}.
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
        void streamsAChunkAsItLiesOnTheHub(@TempDir Path tempDir) throws Exception {
            byte[] content = new byte[200];
            for (int i = 0; i < content.length; i++) {
                content[i] = (byte) (i % 127);
            }
            assertStreams(tempDir, "profile-20260101-120000.jfr.lz4", content);
        }

        @Test
        void streamsAHeapDump(@TempDir Path tempDir) throws Exception {
            assertStreams(tempDir, "heap-dump.hprof", "heap dump content".getBytes());
        }

        @Test
        void streamsAFileTheHubDoesNotClassify(@TempDir Path tempDir) throws Exception {
            assertStreams(tempDir, "notes.txt", "whatever the JVM left there".getBytes());
        }

        @Test
        void aRefusedFileAnswersInvalidArgument() throws Exception {
            var repoManager = mock(RepositoryManager.class);
            when(repoManager.streamFile(SESSION_ID, FILE_ID))
                    .thenThrow(new IllegalArgumentException("Cannot download a transient file: fileId=" + FILE_ID));

            var stub = startServer(serviceWithSession(repoManager));
            var observer = new TestStreamObserver();

            stub.downloadFile(request(SESSION_ID, FILE_ID), observer);

            assertTrue(observer.errorLatch.await(5, TimeUnit.SECONDS));
            assertStatus(Status.Code.INVALID_ARGUMENT, observer.error);
        }

        private void assertStreams(Path tempDir, String name, byte[] content) throws Exception {
            Path tempFile = tempDir.resolve(name);
            Files.write(tempFile, content);

            var repoManager = mock(RepositoryManager.class);
            when(repoManager.streamFile(SESSION_ID, FILE_ID))
                    .thenReturn(new StreamedFile(name, tempFile));

            var stub = startServer(serviceWithSession(repoManager));
            var observer = new TestStreamObserver();

            stub.downloadFile(request(SESSION_ID, FILE_ID), observer);

            assertTrue(observer.completeLatch.await(5, TimeUnit.SECONDS));
            assertNull(observer.error, "Stream should complete without error");
            assertFalse(observer.chunks.isEmpty(), "Should receive at least one chunk");

            assertTotalSizeOnFirstChunkOnly(observer.chunks, content.length);
            assertArrayEquals(content, reassemble(observer.chunks));
        }

        private static DownloadFileRequest request(String sessionId, String fileId) {
            return DownloadFileRequest.newBuilder()
                    .setSessionId(sessionId)
                    .setFileId(fileId)
                    .build();
        }
    }

    // ========== Helpers ==========

    private static final ProjectInfo TEST_PROJECT_INFO =
            new ProjectInfo(PROJECT_ID, null, null, null, null, null, null, null, null, null);

    /**
     * Creates a service where {@code repositoryManagerForSession(SESSION_ID)} succeeds.
     */
    private FileDownloadGrpcService serviceWithSession(RepositoryManager repoManager) {
        var sessionWithRepo = mock(SessionWithRepository.class);
        when(sessionWithRepo.projectInfo()).thenReturn(TEST_PROJECT_INFO);

        var platformRepositories = mock(HubPlatformRepositories.class);
        when(platformRepositories.findSessionWithRepositoryById(SESSION_ID)).thenReturn(Optional.of(sessionWithRepo));

        var repoManagerFactory = mock(RepositoryManager.Factory.class);
        when(repoManagerFactory.apply(TEST_PROJECT_INFO)).thenReturn(repoManager);

        return new FileDownloadGrpcService(new GrpcLookups(platformRepositories, repoManagerFactory, null));
    }

    /**
     * Creates a service where {@code findSessionWithRepositoryById("non-existent")} returns empty.
     */
    private FileDownloadGrpcService serviceWithNoSession() {
        var platformRepositories = mock(HubPlatformRepositories.class);
        when(platformRepositories.findSessionWithRepositoryById("non-existent")).thenReturn(Optional.empty());

        var repoManagerFactory = mock(RepositoryManager.Factory.class);

        return new FileDownloadGrpcService(new GrpcLookups(platformRepositories, repoManagerFactory, null));
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
