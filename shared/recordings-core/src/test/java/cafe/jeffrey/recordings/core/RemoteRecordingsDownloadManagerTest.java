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

import cafe.jeffrey.hub.client.RecordingStreamClient;
import cafe.jeffrey.hub.client.RepositoryClient;
import cafe.jeffrey.hub.client.dto.RecordingSessionResponse;
import cafe.jeffrey.hub.client.dto.RepositoryFileResponse;
import cafe.jeffrey.hub.client.manager.TempDirProvider;
import cafe.jeffrey.recordings.core.download.FileProgress;
import cafe.jeffrey.recordings.core.download.ProgressCallback;
import cafe.jeffrey.recordings.core.manager.RecordingsCoreManager;
import cafe.jeffrey.shared.common.filesystem.TempDirectory;
import cafe.jeffrey.shared.common.model.repository.ChunkWindow;
import cafe.jeffrey.shared.common.model.repository.RecordingStatus;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers what every download path owes its caller: the id of the recording it created, and a
 * recording holding every file that was asked for.
 * <p>
 * Nothing here merges. A session's recording files are fetched one at a time and kept as the
 * several files the recording is made of, so the assertions are about which files were fetched
 * and which ones reached storage.
 */
class RemoteRecordingsDownloadManagerTest {

    private static final String SESSION_ID = "session-1";
    private static final String RECORDING_ID = "local-recording-1";
    private static final String PROJECT_NAME = "checkout";
    private static final Instant CREATED_AT = Instant.parse("2026-03-01T12:00:00Z");

    private final RepositoryClient repositoryClient = mock(RepositoryClient.class);
    private final RecordingStreamClient streamClient = mock(RecordingStreamClient.class);
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
                tempDirProvider, streamClient, repositoryClient, recordingsManager, originContext, PROJECT_NAME);

        when(recordingsManager.createDownloadedRecording(any(), anyList(), anyList(), any()))
                .thenReturn(RECORDING_ID);
    }

    /**
     * Serves every recording and artifact file the hub is asked for. Each transfer hands the
     * consumer its own bytes, the way the real client does.
     */
    private void servesEveryFile() {
        doAnswer(invocation -> feed(invocation.getArgument(1), invocation.getArgument(2)))
                .when(streamClient).streamRecordingFile(eq(SESSION_ID), any(), any());
        doAnswer(invocation -> feed(invocation.getArgument(1), invocation.getArgument(2)))
                .when(streamClient).streamArtifactFile(eq(SESSION_ID), any(), any());
    }

    /**
     * A progress channel that can be cancelled and remembers whether it was told the download
     * failed — the distinction the wrapping of a future's exception used to hide.
     */
    private static final class RecordingProgress implements ProgressCallback {

        private volatile boolean cancelled;
        private volatile boolean cancelOnFileStart;
        private volatile boolean errored;

        /**
         * Cancels from inside a transfer rather than before one, so the exception is raised on a
         * worker and reaches the download wrapped, the way a reader's cancellation does.
         */
        private void cancelOnceATransferStarts() {
            cancelOnFileStart = true;
        }

        @Override
        public void onStart(int totalFiles, long totalBytes) {
        }

        @Override
        public void onFilesDiscovered(List<FileProgress> pendingFiles) {
        }

        @Override
        public void onFileStart(String fileName, long fileSize) {
            if (cancelOnFileStart) {
                cancelled = true;
            }
        }

        @Override
        public void onFileProgress(String fileName, long bytesDownloaded) {
        }

        @Override
        public void onFileComplete(String fileName) {
        }

        @Override
        public void onFileError(String fileName, String errorMessage) {
        }

        @Override
        public void onProcessing() {
        }

        @Override
        public void onComplete() {
        }

        @Override
        public void onError(String errorMessage) {
            errored = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }

    private static Object feed(String fileId, RecordingStreamClient.InputStreamConsumer consumer) throws Exception {
        byte[] bytes = ("bytes-of-" + fileId).getBytes(StandardCharsets.UTF_8);
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            consumer.accept(in, bytes.length);
        }
        return null;
    }

    private static RepositoryFileResponse file(
            String id, String name, SupportedRecordingFile type, RecordingStatus status) {
        return file(id, name, type, status, CREATED_AT);
    }

    private static RepositoryFileResponse file(
            String id, String name, SupportedRecordingFile type, RecordingStatus status, Instant createdAt) {
        return new RepositoryFileResponse(
                id, name, createdAt.toEpochMilli(), 1024L, type, status, type == SupportedRecordingFile.JFR);
    }

    private static RecordingSessionResponse session(RepositoryFileResponse... files) {
        return new RecordingSessionResponse(
                SESSION_ID, "session-name", "inst-1",
                CREATED_AT.toEpochMilli(), CREATED_AT.plusSeconds(60).toEpochMilli(),
                RecordingStatus.FINISHED, 60_000L, List.of(files), false);
    }

    private static RecordingSessionResponse threeChunks() {
        return session(
                file("f-1", "profile-1.jfr", SupportedRecordingFile.JFR, RecordingStatus.FINISHED, CREATED_AT),
                file("f-2", "profile-2.jfr", SupportedRecordingFile.JFR, RecordingStatus.FINISHED, CREATED_AT.plusSeconds(20)),
                file("f-3", "profile-3.jfr", SupportedRecordingFile.JFR, RecordingStatus.FINISHED, CREATED_AT.plusSeconds(40)),
                file("f-4", "heap.hprof", SupportedRecordingFile.HEAP_DUMP, RecordingStatus.FINISHED));
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<Path>> capturedRecordingFiles() {
        return ArgumentCaptor.forClass(List.class);
    }

    @Nested
    @DisplayName("Downloading a session")
    class DownloadSession {

        @Test
        void returnsTheIdOfTheRecordingItCreated() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(session(
                    file("f-1", "recording.jfr", SupportedRecordingFile.JFR, RecordingStatus.FINISHED)));
            servesEveryFile();

            assertEquals(RECORDING_ID, manager.downloadSession(SESSION_ID));
        }

        @Test
        void tagsTheRecordingWithTheUpstreamSessionId() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(threeChunks());
            servesEveryFile();

            manager.downloadSession(SESSION_ID);

            Map<String, String> expectedTags = originContext.toTagMap(SESSION_ID);
            verify(recordingsManager).createDownloadedRecording(any(), anyList(), anyList(), eq(expectedTags));
        }

        /**
         * The heart of it: every chunk of the session reaches storage as a file of its own. Before,
         * the hub concatenated them and this was one file — which is what made a skipped chunk
         * invisible and what cost the hub a full decompress and recompress of the session.
         */
        @Test
        @DisplayName("fetches every recording file separately and keeps them all")
        void keepsEveryChunkAsItsOwnFile() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(threeChunks());
            servesEveryFile();
            ArgumentCaptor<List<Path>> recordingFiles = capturedRecordingFiles();

            manager.downloadSession(SESSION_ID);

            verify(streamClient).streamRecordingFile(eq(SESSION_ID), eq("f-1"), any());
            verify(streamClient).streamRecordingFile(eq(SESSION_ID), eq("f-2"), any());
            verify(streamClient).streamRecordingFile(eq(SESSION_ID), eq("f-3"), any());
            verify(recordingsManager).createDownloadedRecording(
                    any(), recordingFiles.capture(), anyList(), any());

            assertEquals(
                    List.of("profile-1.jfr", "profile-2.jfr", "profile-3.jfr"),
                    recordingFiles.getValue().stream()
                            .map(path -> path.getFileName().toString())
                            .sorted()
                            .toList());
        }

        @Test
        @DisplayName("brings the session's artifacts alongside them")
        void bringsArtifactsAlongside() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(threeChunks());
            servesEveryFile();
            ArgumentCaptor<List<Path>> artifacts = capturedRecordingFiles();

            manager.downloadSession(SESSION_ID);

            verify(streamClient).streamArtifactFile(eq(SESSION_ID), eq("f-4"), any());
            verify(recordingsManager).createDownloadedRecording(any(), anyList(), artifacts.capture(), any());
            assertEquals(List.of("heap.hprof"), artifacts.getValue().stream()
                    .map(path -> path.getFileName().toString())
                    .toList());
        }
    }

    @Nested
    @DisplayName("When a file does not arrive")
    class Failures {

        /**
         * A recording file that did not arrive fails the download. Dropping it the way a missing
         * artifact is dropped would leave a profile with a hole in it that reads as a quiet stretch
         * — the same defect the contiguity rule exists to prevent, reached by a different route.
         */
        @Test
        @DisplayName("a missing recording file fails the whole download")
        void aMissingRecordingFileIsFatal() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(threeChunks());
            servesEveryFile();
            doThrow(new IllegalStateException("hub went away"))
                    .when(streamClient).streamRecordingFile(eq(SESSION_ID), eq("f-2"), any());

            assertThrows(CompletionException.class, () -> manager.downloadSession(SESSION_ID));

            verify(recordingsManager, never()).createDownloadedRecording(any(), anyList(), anyList(), any());
        }

        /**
         * Cancelled once a transfer is already running, so the CancellationException is raised
         * inside a future and comes back wrapped in a CompletionException — which is every
         * cancellation a reader can actually cause, the checks before the first transfer being the
         * only ones that throw on the calling thread. Read as it comes back it is indistinguishable
         * from a failure: the download reports itself failed, flipping a task the reader had just
         * cancelled, and raises a notification for a stop the reader asked for.
         */
        @Test
        @DisplayName("a transfer cancelled midway reports cancellation, not failure")
        void aCancelledTransferIsNotAFailure() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(threeChunks());
            servesEveryFile();
            RecordingProgress progress = new RecordingProgress();
            progress.cancelOnceATransferStarts();

            assertThrows(CancellationException.class,
                    () -> manager.downloadRecordingsWithProgress(SESSION_ID, List.of("f-1", "f-2", "f-3"), progress));

            assertFalse(progress.errored, "a cancelled download must not be reported as an error");
        }

        /**
         * An artifact is supplementary, so the recording is still worth having without it — but the
         * result looks complete, which is why it is reported rather than merely logged.
         */
        @Test
        @DisplayName("a missing artifact is tolerated and the recording still lands")
        void aMissingArtifactIsTolerated() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(threeChunks());
            servesEveryFile();
            doThrow(new IllegalStateException("no heap dump here"))
                    .when(streamClient).streamArtifactFile(eq(SESSION_ID), eq("f-4"), any());
            ArgumentCaptor<List<Path>> artifacts = capturedRecordingFiles();

            assertEquals(RECORDING_ID, manager.downloadSession(SESSION_ID));

            verify(recordingsManager).createDownloadedRecording(any(), anyList(), artifacts.capture(), any());
            assertTrue(artifacts.getValue().isEmpty(), "the lost artifact is simply absent");
        }
    }

    /**
     * The UI's "download session" names every file, and that recording is the session; a pick of
     * some recording files is a part of it and carries the window tag like a window does.
     */
    @Nested
    @DisplayName("A part of a session")
    class PartOfASession {

        private static final Instant SESSION_END = CREATED_AT.plusSeconds(60);

        @Test
        void aWindowFetchesOnlyTheCoveringFilesAndNothingElse() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(threeChunks());
            servesEveryFile();

            String recordingId = manager.downloadWindow(SESSION_ID,
                    new ChunkWindow(CREATED_AT.plusSeconds(25), CREATED_AT.plusSeconds(35)));

            assertEquals(RECORDING_ID, recordingId);
            verify(streamClient).streamRecordingFile(eq(SESSION_ID), eq("f-2"), any());
            verify(streamClient, never()).streamRecordingFile(eq(SESSION_ID), eq("f-1"), any());
            verify(streamClient, never()).streamRecordingFile(eq(SESSION_ID), eq("f-3"), any());
            verify(streamClient, never()).streamArtifactFile(any(), any(), any());
        }

        @Test
        void aWindowIsTaggedAndNamedAfterTheSpanItsFilesCover() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(threeChunks());
            servesEveryFile();
            ArgumentCaptor<String> recordingName = ArgumentCaptor.forClass(String.class);

            manager.downloadWindow(SESSION_ID, new ChunkWindow(CREATED_AT.plusSeconds(45), null));

            Map<String, String> expectedTags = new LinkedHashMap<>(originContext.toTagMap(SESSION_ID));
            expectedTags.put(OriginContext.TAG_WINDOW,
                    CREATED_AT.plusSeconds(40).toEpochMilli() + "-" + SESSION_END.toEpochMilli());
            verify(recordingsManager).createDownloadedRecording(
                    recordingName.capture(), anyList(), anyList(), eq(expectedTags));
            assertEquals("checkout_2026-03-01T12-00-00Z_2026-03-01T12-00-40Z_2026-03-01T12-01-00Z",
                    recordingName.getValue());
        }

        @Test
        void aWindowNoFileCoversIsRefused() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(threeChunks());

            assertThrows(IllegalArgumentException.class, () -> manager.downloadWindow(SESSION_ID,
                    new ChunkWindow(SESSION_END.plusSeconds(600), SESSION_END.plusSeconds(1200))));
            verify(recordingsManager, never()).createDownloadedRecording(any(), anyList(), anyList(), any());
        }

        @Test
        void aWholeSessionIsNamedAfterTheProjectAndTheSessionStart() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(threeChunks());
            servesEveryFile();
            ArgumentCaptor<String> recordingName = ArgumentCaptor.forClass(String.class);

            manager.downloadSession(SESSION_ID);

            verify(recordingsManager).createDownloadedRecording(
                    recordingName.capture(), anyList(), anyList(), any());
            assertEquals("checkout_2026-03-01T12-00-00Z", recordingName.getValue());
        }

        /**
         * An id the session does not hold used to be dropped by the filter that picked the files,
         * returning a recording made of the rest with nothing saying a file had been asked for and
         * not brought. The hub caught it while it still saw whole selections; it serves one file
         * per call now, so the refusal lives here beside the gap check.
         */
        @Test
        void anIdTheSessionDoesNotHoldIsRefusedRatherThanDropped() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(threeChunks());

            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> manager.downloadRecordings(SESSION_ID, List.of("f-1", "f-2", "f-mistyped")));

            assertTrue(e.getMessage().contains("f-mistyped"), e.getMessage());
            verify(streamClient, never()).streamRecordingFile(any(), any(), any());
            verify(recordingsManager, never()).createDownloadedRecording(any(), anyList(), anyList(), any());
        }

        /**
         * An unfinished file is one the session does not hold yet, and reads the same way: named
         * rather than quietly left out of the recording that comes back.
         */
        @Test
        void anUnfinishedFileIsRefusedRatherThanDropped() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(session(
                    file("f-1", "profile-1.jfr", SupportedRecordingFile.JFR, RecordingStatus.FINISHED),
                    file("f-2", "profile-2.jfr", SupportedRecordingFile.JFR, RecordingStatus.ACTIVE,
                            CREATED_AT.plusSeconds(20))));

            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> manager.downloadRecordings(SESSION_ID, List.of("f-1", "f-2")));

            assertTrue(e.getMessage().contains("f-2"), e.getMessage());
            verify(streamClient, never()).streamRecordingFile(any(), any(), any());
        }

        /**
         * A gapped pick still yields a recording claiming a span half of which it does not hold:
         * the window is taken across the files, so the hole is invisible in the result whether or
         * not the files were joined. Refused before the transfer rather than after it.
         */
        @Test
        void aPickWithAChunkSkippedIsRefusedBeforeAnythingIsTransferred() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(threeChunks());

            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> manager.downloadRecordings(SESSION_ID, List.of("f-1", "f-3")));

            assertTrue(e.getMessage().contains("profile-2.jfr"), e.getMessage());
            verify(streamClient, never()).streamRecordingFile(any(), any(), any());
            verify(recordingsManager, never()).createDownloadedRecording(any(), anyList(), anyList(), any());
        }

        @Test
        void aPickWithAChunkSkippedIsRefusedOnTheProgressPathToo() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(threeChunks());
            ProgressCallback progress = mock(ProgressCallback.class);

            assertThrows(IllegalArgumentException.class, () -> manager.downloadRecordingsWithProgress(
                    SESSION_ID, List.of("f-1", "f-3"), progress));

            verify(streamClient, never()).streamRecordingFile(any(), any(), any());
            verify(progress, never()).onStart(anyInt(), anyLong());
        }

        @Test
        void anUnbrokenRunOfChunksIsAccepted() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(threeChunks());
            servesEveryFile();

            assertEquals(RECORDING_ID, manager.downloadRecordings(SESSION_ID, List.of("f-1", "f-2")));
        }

        @Test
        void namingEveryFileIsTheWholeSessionAndNamingSomeIsAPart() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(threeChunks());
            servesEveryFile();
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, String>> tags = ArgumentCaptor.forClass(Map.class);

            manager.downloadRecordings(SESSION_ID, List.of("f-1", "f-2", "f-3", "f-4"));
            manager.downloadRecordings(SESSION_ID, List.of("f-2", "f-4"));

            verify(recordingsManager, times(2))
                    .createDownloadedRecording(any(), anyList(), anyList(), tags.capture());
            assertFalse(tags.getAllValues().get(0).containsKey(OriginContext.TAG_WINDOW));
            assertEquals(CREATED_AT.plusSeconds(20).toEpochMilli() + "-" + CREATED_AT.plusSeconds(40).toEpochMilli(),
                    tags.getAllValues().get(1).get(OriginContext.TAG_WINDOW));
        }
    }

    @Nested
    @DisplayName("Reporting progress")
    class WithProgress {

        @Test
        void returnsTheIdOfTheRecordingItCreated() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(session(
                    file("f-1", "recording.jfr", SupportedRecordingFile.JFR, RecordingStatus.FINISHED)));
            servesEveryFile();
            ProgressCallback progress = mock(ProgressCallback.class);

            String recordingId = manager.downloadRecordingsWithProgress(
                    SESSION_ID, List.of("f-1"), progress);

            assertEquals(RECORDING_ID, recordingId);
            verify(progress).onComplete();
        }

        /**
         * Every file is counted, recordings included. It used to report one file for the whole set
         * of chunks, so the bar jumped the moment the first frame arrived.
         */
        @Test
        @DisplayName("counts every file, not one for the recording and one per artifact")
        void countsEveryFile() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(threeChunks());
            servesEveryFile();
            ProgressCallback progress = mock(ProgressCallback.class);

            manager.downloadRecordingsWithProgress(
                    SESSION_ID, List.of("f-1", "f-2", "f-3", "f-4"), progress);

            verify(progress).onStart(eq(4), anyLong());
            verify(progress).onFileComplete("profile-1.jfr");
            verify(progress).onFileComplete("profile-2.jfr");
            verify(progress).onFileComplete("profile-3.jfr");
            verify(progress).onFileComplete("heap.hprof");
        }
    }
}
