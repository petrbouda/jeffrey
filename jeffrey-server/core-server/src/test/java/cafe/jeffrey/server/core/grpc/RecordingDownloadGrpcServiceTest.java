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

package cafe.jeffrey.server.core.grpc;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cafe.jeffrey.server.api.v1.*;
import cafe.jeffrey.server.core.manager.RepositoryManager;
import cafe.jeffrey.server.persistence.api.SessionWithRepository;
import cafe.jeffrey.server.persistence.api.ServerPlatformRepositories;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.repository.StreamedRecordingFile;

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

class RecordingDownloadGrpcServiceTest {

    private static final String PROJECT_ID = "proj-1";
    private static final String SESSION_ID = "session-1";
    private static final String FILE_ID = "file-1";

    private Server server;
    private ManagedChannel channel;

    private RecordingDownloadServiceGrpc.RecordingDownloadServiceStub startServer(
            RecordingDownloadGrpcService service) throws IOException {

        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name)
                .directExecutor()
                .addService(service)
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(name)
                .directExecutor()
                .build();
        return RecordingDownloadServiceGrpc.newStub(channel);
    }

    @AfterEach
    void shutdown() {
        if (channel != null) {
            channel.shutdownNow();
        }
        if (server != null) {
            server.shutdownNow();
        }
    }

    // ========== DownloadMergedRecordings ==========

    @Nested
    class DownloadMergedRecordings {

        @Test
        void sessionNotFound_returnsNotFound() throws Exception {
            var stub = startServer(serviceWithNoSession());
            var observer = new TestStreamObserver();

            stub.downloadMergedRecordings(
                    DownloadMergedRecordingsRequest.newBuilder()
                            .setSessionId("non-existent")
                            .build(),
                    observer);

            assertTrue(observer.errorLatch.await(5, TimeUnit.SECONDS));
            assertStatus(Status.Code.NOT_FOUND, observer.error);
        }

        @Test
        void streamsFileChunks(@TempDir Path tempDir) throws Exception {
            byte[] content = new byte[200];
            for (int i = 0; i < content.length; i++) {
                content[i] = (byte) (i % 127);
            }
            Path tempFile = tempDir.resolve("merged.jfr");
            Files.write(tempFile, content);

            var repoManager = mock(RepositoryManager.class);
            when(repoManager.mergeAndStreamRecordings(SESSION_ID, List.of("f1", "f2")))
                    .thenReturn(new StreamedRecordingFile("merged.jfr", tempFile));

            var stub = startServer(serviceWithSession(repoManager));
            var observer = new TestStreamObserver();

            stub.downloadMergedRecordings(
                    DownloadMergedRecordingsRequest.newBuilder()
                            .setSessionId(SESSION_ID)
                            .addFileIds("f1")
                            .addFileIds("f2")
                            .build(),
                    observer);

            assertTrue(observer.completeLatch.await(5, TimeUnit.SECONDS));
            assertNull(observer.error, "Stream should complete without error");
            assertFalse(observer.chunks.isEmpty(), "Should receive at least one chunk");

            // Verify every chunk reports the correct total size
            for (DataChunk chunk : observer.chunks) {
                assertEquals(content.length, chunk.getTotalSize());
            }

            // Reassemble chunks and verify content
            ByteArrayOutputStream assembled = new ByteArrayOutputStream();
            for (DataChunk chunk : observer.chunks) {
                chunk.getData().writeTo(assembled);
            }
            assertArrayEquals(content, assembled.toByteArray());
        }
    }

    // ========== DownloadRecordingFile ==========

    @Nested
    class DownloadRecordingFile {

        @Test
        void sessionNotFound_returnsNotFound() throws Exception {
            var stub = startServer(serviceWithNoSession());
            var observer = new TestStreamObserver();

            stub.downloadRecordingFile(
                    DownloadRecordingFileRequest.newBuilder()
                            .setSessionId("non-existent")
                            .setFileId(FILE_ID)
                            .build(),
                    observer);

            assertTrue(observer.errorLatch.await(5, TimeUnit.SECONDS));
            assertStatus(Status.Code.NOT_FOUND, observer.error);
        }

        @Test
        void streamsFileChunks(@TempDir Path tempDir) throws Exception {
            byte[] content = "JFR recording file content for testing".getBytes();
            Path tempFile = tempDir.resolve("recording.jfr");
            Files.write(tempFile, content);

            var repoManager = mock(RepositoryManager.class);
            when(repoManager.streamRecordingFile(SESSION_ID, FILE_ID))
                    .thenReturn(new StreamedRecordingFile("recording.jfr", tempFile));

            var stub = startServer(serviceWithSession(repoManager));
            var observer = new TestStreamObserver();

            stub.downloadRecordingFile(
                    DownloadRecordingFileRequest.newBuilder()
                            .setSessionId(SESSION_ID)
                            .setFileId(FILE_ID)
                            .build(),
                    observer);

            assertTrue(observer.completeLatch.await(5, TimeUnit.SECONDS));
            assertNull(observer.error, "Stream should complete without error");
            assertFalse(observer.chunks.isEmpty(), "Should receive at least one chunk");

            ByteArrayOutputStream assembled = new ByteArrayOutputStream();
            for (DataChunk chunk : observer.chunks) {
                assertEquals(content.length, chunk.getTotalSize());
                chunk.getData().writeTo(assembled);
            }
            assertArrayEquals(content, assembled.toByteArray());
        }
    }

    // ========== DownloadArtifactFile ==========

    @Nested
    class DownloadArtifactFile {

        @Test
        void sessionNotFound_returnsNotFound() throws Exception {
            var stub = startServer(serviceWithNoSession());
            var observer = new TestStreamObserver();

            stub.downloadArtifactFile(
                    DownloadArtifactFileRequest.newBuilder()
                            .setSessionId("non-existent")
                            .setFileId(FILE_ID)
                            .build(),
                    observer);

            assertTrue(observer.errorLatch.await(5, TimeUnit.SECONDS));
            assertStatus(Status.Code.NOT_FOUND, observer.error);
        }

        @Test
        void streamsFileChunks(@TempDir Path tempDir) throws Exception {
            byte[] content = "heap dump artifact content".getBytes();
            Path tempFile = tempDir.resolve("heapdump.hprof");
            Files.write(tempFile, content);

            var repoManager = mock(RepositoryManager.class);
            when(repoManager.streamArtifactFile(SESSION_ID, FILE_ID))
                    .thenReturn(new StreamedRecordingFile("heapdump.hprof", tempFile));

            var stub = startServer(serviceWithSession(repoManager));
            var observer = new TestStreamObserver();

            stub.downloadArtifactFile(
                    DownloadArtifactFileRequest.newBuilder()
                            .setSessionId(SESSION_ID)
                            .setFileId(FILE_ID)
                            .build(),
                    observer);

            assertTrue(observer.completeLatch.await(5, TimeUnit.SECONDS));
            assertNull(observer.error, "Stream should complete without error");
            assertFalse(observer.chunks.isEmpty(), "Should receive at least one chunk");

            ByteArrayOutputStream assembled = new ByteArrayOutputStream();
            for (DataChunk chunk : observer.chunks) {
                assertEquals(content.length, chunk.getTotalSize());
                chunk.getData().writeTo(assembled);
            }
            assertArrayEquals(content, assembled.toByteArray());
        }
    }

    // ========== Helpers ==========

    private static final ProjectInfo TEST_PROJECT_INFO =
            new ProjectInfo(PROJECT_ID, null, null, null, null, null, null, null, null, null);

    /**
     * Creates a service where {@code repositoryManagerForSession(SESSION_ID)} succeeds.
     */
    private RecordingDownloadGrpcService serviceWithSession(RepositoryManager repoManager) {
        var sessionWithRepo = mock(SessionWithRepository.class);
        when(sessionWithRepo.projectInfo()).thenReturn(TEST_PROJECT_INFO);

        var platformRepositories = mock(ServerPlatformRepositories.class);
        when(platformRepositories.findSessionWithRepositoryById(SESSION_ID)).thenReturn(Optional.of(sessionWithRepo));

        var repoManagerFactory = mock(RepositoryManager.Factory.class);
        when(repoManagerFactory.apply(TEST_PROJECT_INFO)).thenReturn(repoManager);

        return new RecordingDownloadGrpcService(platformRepositories, repoManagerFactory);
    }

    /**
     * Creates a service where {@code findSessionWithRepositoryById("non-existent")} returns empty.
     */
    private RecordingDownloadGrpcService serviceWithNoSession() {
        var platformRepositories = mock(ServerPlatformRepositories.class);
        when(platformRepositories.findSessionWithRepositoryById("non-existent")).thenReturn(Optional.empty());

        var repoManagerFactory = mock(RepositoryManager.Factory.class);

        return new RecordingDownloadGrpcService(platformRepositories, repoManagerFactory);
    }

    private static void assertStatus(Status.Code expected, Throwable error) {
        assertNotNull(error, "Expected an error");
        assertInstanceOf(StatusRuntimeException.class, error);
        assertEquals(expected, ((StatusRuntimeException) error).getStatus().getCode());
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
