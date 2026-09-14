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

package cafe.jeffrey.recordings.core;

import cafe.jeffrey.hub.client.FileDownloadClient;
import cafe.jeffrey.hub.client.RepositoryClient;
import cafe.jeffrey.hub.client.dto.RecordingSessionResponse;
import cafe.jeffrey.hub.client.dto.RepositoryFileResponse;
import cafe.jeffrey.hub.client.manager.TempDirProvider;
import cafe.jeffrey.recordings.core.download.ProgressCallback;
import cafe.jeffrey.recordings.core.manager.RecordingsCoreManager;
import cafe.jeffrey.shared.common.exception.JeffreyClientException;
import cafe.jeffrey.shared.common.filesystem.TempDirectory;
import cafe.jeffrey.shared.common.model.repository.RecordingStatus;
import cafe.jeffrey.shared.common.model.repository.SupportedFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the one thing every download path owes its caller: the id of the recording it created.
 * Without it a caller that wants to analyse what it just downloaded has to guess which entry in
 * the local store is the new one.
 */
class RemoteRecordingsDownloadManagerTest {

    private static final String SESSION_ID = "session-1";
    private static final String RECORDING_ID = "local-recording-1";
    private static final Instant CREATED_AT = Instant.parse("2026-03-01T12:00:00Z");

    private final RepositoryClient repositoryClient = mock(RepositoryClient.class);
    private final FileDownloadClient downloadClient = mock(FileDownloadClient.class);
    private final RecordingsCoreManager recordingsManager = mock(RecordingsCoreManager.class);

    private final OriginContext originContext =
            new OriginContext("cfg-prod", "Production", "ws-1", "workspace", "p-1", "checkout");

    @TempDir
    Path tempRoot;

    private RemoteRecordingsDownloadManager manager;

    @BeforeEach
    void setUp() {
        TempDirProvider tempDirProvider = () -> new TempDirectory(tempRoot.resolve("download"));
        manager = new RemoteRecordingsDownloadManager(
                tempDirProvider, downloadClient, repositoryClient, recordingsManager, originContext, "checkout");

        when(recordingsManager.createDownloadedRecording(any(), anyList(), anyList(), any()))
                .thenReturn(RECORDING_ID);
    }

    private static RepositoryFileResponse file(
            String id, String name, SupportedFile type, RecordingStatus status) {
        return file(id, name, type, status, CREATED_AT);
    }

    private static RepositoryFileResponse file(
            String id, String name, SupportedFile type, RecordingStatus status, Instant createdAt) {
        return new RepositoryFileResponse(id, name, createdAt.toEpochMilli(), 1024L, type, status);
    }

    private static RecordingSessionResponse session(RepositoryFileResponse... files) {
        return new RecordingSessionResponse(
                SESSION_ID, "session-name", "inst-1",
                CREATED_AT.toEpochMilli(), CREATED_AT.plusSeconds(60).toEpochMilli(),
                RecordingStatus.FINISHED, 60_000L, List.of(files), false);
    }

    /**
     * A downloaded file as the download client hands it over, holding its own name as content so
     * a stored recording says which chunks went into it and in what order. It reports no
     * filename of its own — the client's resource is a temp file whose name means nothing, and
     * the stored file must be named from the hub's listing instead.
     */
    private static Resource resource(String filename) {
        return new ByteArrayResource(filename.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The hub streams the file's bytes into whatever the download hands it; here, the file's own
     * name, so a stored recording says which chunks went into it and in what order.
     */
    private void streams(String fileId, String filename) {
        doAnswer(invocation -> {
            FileDownloadClient.InputStreamConsumer consumer = invocation.getArgument(2);
            try (InputStream in = resource(filename).getInputStream()) {
                consumer.accept(in, filename.length());
            }
            return null;
        }).when(downloadClient).streamFile(eq(SESSION_ID), eq(fileId), any());
    }

    /**
     * Captures what was persisted as the recording's files — each chunk's content, which is its
     * own name — in the order they were handed over, before the temp directory that held them
     * is gone.
     */
    private AtomicReference<List<String>> capturedRecording() {
        AtomicReference<List<String>> chunks = new AtomicReference<>();
        when(recordingsManager.createDownloadedRecording(any(), anyList(), anyList(), any())).thenAnswer(invocation -> {
            List<Path> recordingFiles = invocation.getArgument(1);
            List<String> contents = new java.util.ArrayList<>();
            for (Path recordingFile : recordingFiles) {
                contents.add(Files.readString(recordingFile));
            }
            chunks.set(contents);
            return RECORDING_ID;
        });
        return chunks;
    }

    private List<String> capturedAdditionalFileNames() {
        List<String> names = new java.util.ArrayList<>();
        when(recordingsManager.createDownloadedRecording(any(), anyList(), anyList(), any())).thenAnswer(invocation -> {
            List<Path> files = invocation.getArgument(2);
            files.forEach(file -> names.add(file.getFileName().toString()));
            return RECORDING_ID;
        });
        return names;
    }

    @Nested
    class DownloadSession {

        @Test
        void returnsTheIdOfTheRecordingItCreated() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(session(
                    file("f-1", "recording.jfr", SupportedFile.JFR, RecordingStatus.FINISHED)));
            streams("f-1", "recording.jfr");

            String recordingId = manager.downloadSession(SESSION_ID);

            assertEquals(RECORDING_ID, recordingId);
        }

        @Test
        void tagsTheRecordingWithTheUpstreamSessionId() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(session(
                    file("f-1", "recording.jfr", SupportedFile.JFR, RecordingStatus.FINISHED)));
            streams("f-1", "recording.jfr");

            manager.downloadSession(SESSION_ID);

            Map<String, String> expectedTags = originContext.toTagMap(SESSION_ID);
            verify(recordingsManager).createDownloadedRecording(
                    eq("checkout_2026-03-01T12-00-00Z"), anyList(), anyList(), eq(expectedTags));
        }

        /**
         * The hub serves chunks one by one and never merges; they are stored as the recording's
         * files, oldest chunk first, whatever order the listing had them in, and nothing is joined.
         */
        @Test
        void storesTheChunksAsTheRecordingsFilesOldestFirst() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(session(
                    file("f-2", "profile-2.jfr", SupportedFile.JFR, RecordingStatus.FINISHED, CREATED_AT.plusSeconds(30)),
                    file("f-1", "profile-1.jfr", SupportedFile.JFR, RecordingStatus.FINISHED, CREATED_AT)));
            streams("f-1", "profile-1.jfr");
            streams("f-2", "profile-2.jfr");
            AtomicReference<List<String>> recording = capturedRecording();

            manager.downloadSession(SESSION_ID);

            assertEquals(List.of("profile-1.jfr", "profile-2.jfr"), recording.get());
        }

        @Test
        void storesEveryOtherFinishedFileBesideTheRecording() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(session(
                    file("f-1", "profile-1.jfr", SupportedFile.JFR, RecordingStatus.FINISHED),
                    file("f-2", "heap.hprof", SupportedFile.HEAP_DUMP, RecordingStatus.FINISHED),
                    file("f-3", "notes.txt", SupportedFile.UNKNOWN, RecordingStatus.FINISHED),
                    file("f-4", "profile-1.jfr.1~", SupportedFile.ASPROF_TEMP, RecordingStatus.FINISHED),
                    file("f-5", "profile-2.jfr", SupportedFile.JFR, RecordingStatus.ACTIVE)));
            streams("f-1", "profile-1.jfr");
            streams("f-2", "heap.hprof");
            streams("f-3", "notes.txt");
            List<String> additionalFiles = capturedAdditionalFileNames();

            manager.downloadSession(SESSION_ID);

            assertEquals(List.of("heap.hprof", "notes.txt"), additionalFiles);
            verify(downloadClient, never()).streamFile(eq(SESSION_ID), eq("f-4"), any());
            verify(downloadClient, never()).streamFile(eq(SESSION_ID), eq("f-5"), any());
        }

        /**
         * The same pipeline runs for a caller with nothing listening: the download is skipped
         * for nobody and the same files are stored.
         */
        @Test
        void aCallerWithoutAListenerGetsTheSameDownload() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(session(
                    file("f-1", "profile-1.jfr", SupportedFile.JFR, RecordingStatus.FINISHED),
                    file("f-2", "gc.jvm-log", SupportedFile.JVM_LOG, RecordingStatus.FINISHED)));
            streams("f-1", "profile-1.jfr");
            streams("f-2", "gc.jvm-log");
            List<String> additionalFiles = capturedAdditionalFileNames();

            assertEquals(RECORDING_ID, manager.downloadSession(SESSION_ID));

            assertEquals(List.of("gc.jvm-log"), additionalFiles);
        }

        @Test
        void refusesASessionWithoutAFinishedChunk() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(session(
                    file("f-1", "heap.hprof", SupportedFile.HEAP_DUMP, RecordingStatus.FINISHED),
                    file("f-2", "profile-1.jfr", SupportedFile.JFR, RecordingStatus.ACTIVE)));

            assertThrows(JeffreyClientException.class, () -> manager.downloadSession(SESSION_ID));
            verify(downloadClient, never()).streamFile(any(), any(), any());
        }
    }

    @Nested
    class DownloadFiles {

        @Test
        void returnsTheIdOfTheRecordingItCreated() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(session(
                    file("f-1", "recording.jfr", SupportedFile.JFR, RecordingStatus.FINISHED),
                    file("f-2", "heap.hprof", SupportedFile.HEAP_DUMP, RecordingStatus.FINISHED)));
            streams("f-1", "recording.jfr");
            streams("f-2", "heap.hprof");

            String recordingId = manager.downloadFiles(SESSION_ID, List.of("f-1", "f-2"));

            assertEquals(RECORDING_ID, recordingId);
        }

        @Test
        void bringsOnlyTheFilesAskedFor() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(session(
                    file("f-1", "profile-1.jfr", SupportedFile.JFR, RecordingStatus.FINISHED),
                    file("f-2", "profile-2.jfr", SupportedFile.JFR, RecordingStatus.FINISHED, CREATED_AT.plusSeconds(30)),
                    file("f-3", "heap.hprof", SupportedFile.HEAP_DUMP, RecordingStatus.FINISHED)));
            streams("f-1", "profile-1.jfr");
            AtomicReference<List<String>> recording = capturedRecording();

            manager.downloadFiles(SESSION_ID, List.of("f-1"));

            assertEquals(List.of("profile-1.jfr"), recording.get());
            verify(downloadClient, never()).streamFile(eq(SESSION_ID), eq("f-2"), any());
            verify(downloadClient, never()).streamFile(eq(SESSION_ID), eq("f-3"), any());
        }
    }

    @Nested
    class DownloadFilesWithProgress {

        @Test
        void returnsTheIdOfTheRecordingItCreated() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(session(
                    file("f-1", "recording.jfr", SupportedFile.JFR, RecordingStatus.FINISHED)));
            streams("f-1", "recording.jfr");

            ProgressCallback progress = mock(ProgressCallback.class);
            String recordingId = manager.downloadFiles(
                    SESSION_ID, List.of("f-1"), progress);

            assertEquals(RECORDING_ID, recordingId);
            verify(progress).onProcessing();
            verify(progress).onComplete();
        }

        @Test
        void reportsEveryFileItBringsAndStoresTheChunks() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(session(
                    file("f-1", "profile-1.jfr", SupportedFile.JFR, RecordingStatus.FINISHED),
                    file("f-2", "profile-2.jfr", SupportedFile.JFR, RecordingStatus.FINISHED, CREATED_AT.plusSeconds(30)),
                    file("f-3", "gc.jvm-log", SupportedFile.JVM_LOG, RecordingStatus.FINISHED)));
            streams("f-1", "profile-1.jfr");
            streams("f-2", "profile-2.jfr");
            streams("f-3", "gc.jvm-log");
            AtomicReference<List<String>> recording = capturedRecording();

            ProgressCallback progress = mock(ProgressCallback.class);
            manager.downloadFiles(SESSION_ID, List.of("f-1", "f-2", "f-3"), progress);

            assertEquals(List.of("profile-1.jfr", "profile-2.jfr"), recording.get());
            verify(progress).onStart(3, 3 * 1024L);
            verify(progress).onFileComplete("profile-1.jfr");
            verify(progress).onFileComplete("profile-2.jfr");
            verify(progress).onFileComplete("gc.jvm-log");
        }

        /**
         * A log that fails to arrive is reported and left out; a chunk that fails to arrive fails
         * the download, because a recording with a chunk missing is not that recording.
         */
        @Test
        void aMissingOtherFileIsSkippedButAMissingChunkFailsTheDownload() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(session(
                    file("f-1", "profile-1.jfr", SupportedFile.JFR, RecordingStatus.FINISHED),
                    file("f-2", "gc.jvm-log", SupportedFile.JVM_LOG, RecordingStatus.FINISHED)));
            streams("f-1", "profile-1.jfr");
            fails("f-2", new IllegalStateException("gone"));
            List<String> additionalFiles = capturedAdditionalFileNames();
            ProgressCallback progress = mock(ProgressCallback.class);

            manager.downloadFiles(SESSION_ID, List.of("f-1", "f-2"), progress);

            assertTrue(additionalFiles.isEmpty());
            verify(progress).onFileError("gc.jvm-log", "gone");
            verify(progress).onComplete();

            fails("f-1", new IllegalStateException("gone"));
            ProgressCallback failing = mock(ProgressCallback.class);

            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> manager.downloadFiles(SESSION_ID, List.of("f-1"), failing));

            // The chunk's own failure, not the CompletionException a future wraps it in: the
            // message the listener shows is the hub's, and the type is what a notification names.
            assertEquals("gone", failure.getMessage());
            verify(failing).onFileError("profile-1.jfr", "gone");
            verify(failing).onError("gone");
            verify(failing, never()).onComplete();
        }

        /**
         * A cancellation that lands while a chunk is being copied is still a cancellation. The
         * copy runs on another thread, whose exception a future hands back wrapped; unwrapped
         * here, so that a listener that cancelled is not told the download failed.
         */
        @Test
        void aCancellationDuringAChunkCopyIsReportedAsACancellationNotAFailure() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(session(
                    file("f-1", "profile-1.jfr", SupportedFile.JFR, RecordingStatus.FINISHED)));
            streams("f-1", "profile-1.jfr");
            ProgressCallback progress = cancellingOnceAFileStarted();

            assertThrows(CancellationException.class,
                    () -> manager.downloadFiles(SESSION_ID, List.of("f-1"), progress));

            verify(progress, never()).onError(any());
            verify(progress, never()).onComplete();
            verify(recordingsManager, never()).createDownloadedRecording(any(), anyList(), anyList(), any());
        }

        /**
         * Once a chunk has failed there is no recording to be had, so the transfers still in
         * flight are stopped rather than run to an end nobody will use.
         */
        @Test
        void aChunkFailureStopsTheTransfersStillInFlight() throws Exception {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(session(
                    file("f-1", "profile-1.jfr", SupportedFile.JFR, RecordingStatus.FINISHED),
                    file("f-2", "profile-2.jfr", SupportedFile.JFR, RecordingStatus.FINISHED, CREATED_AT.plusSeconds(30))));
            ProgressCallback progress = mock(ProgressCallback.class);
            CountDownLatch firstChunkFailed = new CountDownLatch(1);
            doAnswer(invocation -> {
                firstChunkFailed.countDown();
                return null;
            }).when(progress).onFileError(eq("profile-1.jfr"), any());
            fails("f-1", new IllegalStateException("gone"));
            // The second chunk's bytes arrive only after the first has failed, so its copy loop
            // sees the stop rather than racing it.
            streamsAfter("f-2", "profile-2.jfr", firstChunkFailed);

            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> manager.downloadFiles(SESSION_ID, List.of("f-1", "f-2"), progress));

            assertEquals("gone", failure.getMessage());
            verify(progress).onFileError(eq("profile-2.jfr"), any());
            verify(progress, never()).onFileComplete("profile-2.jfr");
            verify(progress).onError("gone");
        }

        /**
         * A hub that streams more than it announced for a file is not sending what it listed;
         * the file is refused rather than written for as long as the hub cares to send.
         */
        @Test
        void aFileThatRunsPastItsAnnouncedSizeIsRefused() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(session(
                    file("f-1", "profile-1.jfr", SupportedFile.JFR, RecordingStatus.FINISHED),
                    file("f-2", "gc.jvm-log", SupportedFile.JVM_LOG, RecordingStatus.FINISHED)));
            streams("f-1", "profile-1.jfr");
            doAnswer(invocation -> {
                FileDownloadClient.InputStreamConsumer consumer = invocation.getArgument(2);
                try (InputStream in = resource("a much longer log than announced").getInputStream()) {
                    consumer.accept(in, 3);
                }
                return null;
            }).when(downloadClient).streamFile(eq(SESSION_ID), eq("f-2"), any());
            List<String> additionalFiles = capturedAdditionalFileNames();
            ProgressCallback progress = mock(ProgressCallback.class);

            manager.downloadFiles(SESSION_ID, List.of("f-1", "f-2"), progress);

            assertTrue(additionalFiles.isEmpty());
            verify(progress).onFileError(eq("gc.jvm-log"), any());
        }

        /**
         * A name off the wire is one path element or nothing: a hub listing a file as
         * {@code ../escape.log} does not get to say where on this disk it lands.
         */
        @Test
        void aFileNameThatIsNotAPlainNameIsRefused() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(session(
                    file("f-1", "profile-1.jfr", SupportedFile.JFR, RecordingStatus.FINISHED),
                    file("f-2", "../escape.log", SupportedFile.APP_LOG, RecordingStatus.FINISHED)));
            streams("f-1", "profile-1.jfr");
            streams("f-2", "../escape.log");
            List<String> additionalFiles = capturedAdditionalFileNames();
            ProgressCallback progress = mock(ProgressCallback.class);

            manager.downloadFiles(SESSION_ID, List.of("f-1", "f-2"), progress);

            assertTrue(additionalFiles.isEmpty());
            assertFalse(Files.exists(tempRoot.resolve("escape.log")));
            verify(progress).onFileError(eq("../escape.log"), any());

            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(session(
                    file("f-3", "../escape.jfr", SupportedFile.JFR, RecordingStatus.FINISHED)));
            streams("f-3", "../escape.jfr");

            assertThrows(IllegalArgumentException.class, () -> manager.downloadFiles(SESSION_ID, List.of("f-3")));
            assertFalse(Files.exists(tempRoot.resolve("escape.jfr")));
        }

        private void fails(String fileId, RuntimeException failure) {
            doAnswer(invocation -> {
                throw failure;
            }).when(downloadClient).streamFile(eq(SESSION_ID), eq(fileId), any());
        }

        /**
         * Streams the file's bytes once the latch is released, holding the consumer until then.
         */
        private void streamsAfter(String fileId, String filename, CountDownLatch gate) {
            doAnswer(invocation -> {
                FileDownloadClient.InputStreamConsumer consumer = invocation.getArgument(2);
                InputStream bytes = resource(filename).getInputStream();
                InputStream gated = new InputStream() {
                    @Override
                    public int read() throws IOException {
                        awaitGate();
                        return bytes.read();
                    }

                    @Override
                    public int read(byte[] b, int off, int len) throws IOException {
                        awaitGate();
                        return bytes.read(b, off, len);
                    }

                    private void awaitGate() throws IOException {
                        try {
                            if (!gate.await(5, TimeUnit.SECONDS)) {
                                throw new IOException("the first chunk never failed");
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IOException(e);
                        }
                    }
                };
                try (bytes) {
                    consumer.accept(gated, filename.length());
                }
                return null;
            }).when(downloadClient).streamFile(eq(SESSION_ID), eq(fileId), any());
        }

        /**
         * A listener that cancels as soon as the first file starts, the way a user does who
         * clicked Cancel while the bytes were coming in.
         */
        private static ProgressCallback cancellingOnceAFileStarted() {
            AtomicBoolean cancelled = new AtomicBoolean(false);
            ProgressCallback progress = mock(ProgressCallback.class);
            doAnswer(invocation -> {
                cancelled.set(true);
                return null;
            }).when(progress).onFileStart(any(), org.mockito.ArgumentMatchers.anyLong());
            doAnswer(invocation -> cancelled.get()).when(progress).isCancelled();
            return progress;
        }
    }
}
