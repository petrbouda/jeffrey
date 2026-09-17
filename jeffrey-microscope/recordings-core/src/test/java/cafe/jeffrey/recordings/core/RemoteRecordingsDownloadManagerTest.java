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

import cafe.jeffrey.hub.client.FileStreamClient;
import cafe.jeffrey.hub.client.RepositoryClient;
import cafe.jeffrey.hub.client.dto.RecordingSessionResponse;
import cafe.jeffrey.hub.client.dto.RepositoryFileResponse;
import cafe.jeffrey.hub.client.manager.TempDirProvider;
import cafe.jeffrey.recordings.core.download.FileProgress;
import cafe.jeffrey.recordings.core.download.ProgressCallback;
import cafe.jeffrey.recordings.core.manager.RecordingsCoreManager;
import cafe.jeffrey.shared.common.filesystem.TempDirectory;
import cafe.jeffrey.microscope.model.repository.ChunkWindow;
import cafe.jeffrey.microscope.model.repository.RecordingStatus;
import cafe.jeffrey.storage.recording.api.file.ManagedFile;
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
    private final FileStreamClient streamClient = mock(FileStreamClient.class);
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
        doAnswer(invocation -> {
            String fileId = invocation.getArgument(1);
            return feedAs(fileId, listedNameOf(fileId), invocation.getArgument(2));
        }).when(streamClient).streamFile(eq(SESSION_ID), any(), any());
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

    /**
     * What the session's listing calls this file — which is the name the hub sends back when
     * nothing has renamed it since.
     */
    private String listedNameOf(String fileId) {
        return repositoryClient.recordingSession(SESSION_ID).files().stream()
                .filter(file -> file.id().equals(fileId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no fixture file with id " + fileId))
                .name();
    }

    /**
     * Serves the file under a name of the hub's choosing, the way the hub does once the
     * compression job has replaced a chunk with its archive.
     */
    private static Object feedAs(
            String fileId, String sentAs, FileStreamClient.InputStreamConsumer consumer) throws Exception {

        byte[] bytes = ("bytes-of-" + fileId).getBytes(StandardCharsets.UTF_8);
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            consumer.accept(in, new FileStreamClient.TransferredFile(sentAs, bytes.length));
        }
        return null;
    }

    private static RepositoryFileResponse file(
            String id, String name, ManagedFile type) {
        return file(id, name, type, CREATED_AT);
    }

    /**
     * The status column is the one the hub does not send: a file carries none, and the reader
     * fills it in from the session. It is FINISHED here because these sessions have finished.
     */
    private static RepositoryFileResponse file(
            String id, String name, ManagedFile type, Instant createdAt) {
        return new RepositoryFileResponse(
                id, name, createdAt.toEpochMilli(), 1024L, type, RecordingStatus.FINISHED,
                type == ManagedFile.JFR);
    }

    /** A chunk a profiler was stopped before it wrote anything into. */
    private static RepositoryFileResponse emptyFile(String id, String name, Instant createdAt) {
        return new RepositoryFileResponse(
                id, name, createdAt.toEpochMilli(), 0L, ManagedFile.JFR, RecordingStatus.FINISHED, true);
    }

    private static RecordingSessionResponse session(RepositoryFileResponse... files) {
        return new RecordingSessionResponse(
                SESSION_ID, "session-name", "inst-1",
                CREATED_AT.toEpochMilli(), CREATED_AT.plusSeconds(60).toEpochMilli(),
                RecordingStatus.FINISHED, 60_000L, List.of(files), false);
    }

    /**
     * A session that is still recording, so the newest of these files is the chunk the profiler
     * holds open and nothing may download it.
     */
    private static RecordingSessionResponse liveSession(RepositoryFileResponse... files) {
        return new RecordingSessionResponse(
                SESSION_ID, "session-name", "inst-1",
                CREATED_AT.toEpochMilli(), null,
                RecordingStatus.ACTIVE, null, List.of(files), false);
    }

    private static RecordingSessionResponse threeChunks() {
        return session(
                file("f-1", "profile-1.jfr", ManagedFile.JFR, CREATED_AT),
                file("f-2", "profile-2.jfr", ManagedFile.JFR, CREATED_AT.plusSeconds(20)),
                file("f-3", "profile-3.jfr", ManagedFile.JFR, CREATED_AT.plusSeconds(40)),
                file("f-4", "heap.hprof", ManagedFile.HEAP_DUMP));
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
                    file("f-1", "recording.jfr", ManagedFile.JFR)));
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
         * The listing a reader holds is older than the transfer it asks for, and in between the
         * compression job can replace a chunk with its archive. The bytes that arrive are then
         * the archive's, so the name has to be the archive's too: written as {@code .jfr}, an LZ4
         * frame is read as a raw recording and fails on the chunk magic, because compression is
         * recognised by the extension and nothing sniffs the frame.
         */
        @Test
        @DisplayName("keeps a file under the name the hub sent, not the one the listing had")
        void keepsTheNameTheTransferCarried() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(session(
                    file("f-1", "profile-1.jfr", ManagedFile.JFR)));
            doAnswer(invocation -> feedAs(
                    invocation.getArgument(1), "profile-1.jfr.lz4", invocation.getArgument(2)))
                    .when(streamClient).streamFile(eq(SESSION_ID), any(), any());
            ArgumentCaptor<List<Path>> recordingFiles = capturedRecordingFiles();

            manager.downloadSession(SESSION_ID);

            verify(recordingsManager).createDownloadedRecording(
                    any(), recordingFiles.capture(), anyList(), any());

            assertEquals(
                    List.of("profile-1.jfr.lz4"),
                    recordingFiles.getValue().stream().map(path -> path.getFileName().toString()).toList());
        }

        /**
         * The name arrives over the wire from a machine this one does not control, and is about to
         * be resolved into a directory. It is reduced to a single path element first, so one
         * carrying {@code ../} writes beside its siblings instead of above them.
         */
        @Test
        @DisplayName("a transferred name cannot climb out of the directory it lands in")
        void reducesATransferredNameToASinglePathElement() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(session(
                    file("f-1", "profile-1.jfr", ManagedFile.JFR)));
            doAnswer(invocation -> feedAs(
                    invocation.getArgument(1), "../../escaped.jfr", invocation.getArgument(2)))
                    .when(streamClient).streamFile(eq(SESSION_ID), any(), any());
            ArgumentCaptor<List<Path>> recordingFiles = capturedRecordingFiles();

            manager.downloadSession(SESSION_ID);

            verify(recordingsManager).createDownloadedRecording(
                    any(), recordingFiles.capture(), anyList(), any());

            Path landed = recordingFiles.getValue().getFirst();
            assertEquals("escaped.jfr", landed.getFileName().toString());
            assertTrue(landed.normalize().startsWith(tempRoot),
                    "a name off the wire must not reach outside the download directory: " + landed);
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

            verify(streamClient).streamFile(eq(SESSION_ID), eq("f-1"), any());
            verify(streamClient).streamFile(eq(SESSION_ID), eq("f-2"), any());
            verify(streamClient).streamFile(eq(SESSION_ID), eq("f-3"), any());
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

            verify(streamClient).streamFile(eq(SESSION_ID), eq("f-4"), any());
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
         * A zero-byte chunk is left behind by a profiler stopped before it wrote an event — a
         * killed container, a shutdown that was not graceful — and it is the last chunk that gets
         * it. The hub refuses to serve one, so asking for it fails the whole session's download;
         * the rest of the session is worth having without it.
         */
        @Test
        @DisplayName("an empty chunk is left out rather than failing the download")
        void anEmptyChunkIsLeftOut() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(session(
                    file("f-1", "profile-1.jfr", ManagedFile.JFR, CREATED_AT),
                    emptyFile("f-2", "profile-2.jfr", CREATED_AT.plusSeconds(20))));
            servesEveryFile();

            manager.downloadSession(SESSION_ID);

            verify(streamClient, never()).streamFile(eq(SESSION_ID), eq("f-2"), any());
            ArgumentCaptor<List<Path>> recordings = capturedRecordingFiles();
            verify(recordingsManager)
                    .createDownloadedRecording(any(), recordings.capture(), anyList(), any());
            assertEquals(
                    List.of("profile-1.jfr"),
                    recordings.getValue().stream().map(path -> path.getFileName().toString()).toList());
        }

        /**
         * And when emptiness is all the session has, there is nothing to download: the refusal is
         * the same one a session with no recording at all gets, rather than a recording of no
         * files.
         */
        @Test
        @DisplayName("a session of nothing but empty chunks is refused")
        void aSessionOfNothingButEmptyChunksIsRefused() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(session(
                    emptyFile("f-1", "profile-1.jfr", CREATED_AT)));

            assertThrows(RuntimeException.class, () -> manager.downloadSession(SESSION_ID));

            verify(recordingsManager, never()).createDownloadedRecording(any(), anyList(), anyList(), any());
        }

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
                    .when(streamClient).streamFile(eq(SESSION_ID), eq("f-2"), any());

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
                    .when(streamClient).streamFile(eq(SESSION_ID), eq("f-4"), any());
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
            verify(streamClient).streamFile(eq(SESSION_ID), eq("f-2"), any());
            verify(streamClient, never()).streamFile(eq(SESSION_ID), eq("f-1"), any());
            verify(streamClient, never()).streamFile(eq(SESSION_ID), eq("f-3"), any());
            verify(streamClient, never()).streamFile(eq(SESSION_ID), eq("f-4"), any());
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
            verify(streamClient, never()).streamFile(any(), any(), any());
            verify(recordingsManager, never()).createDownloadedRecording(any(), anyList(), anyList(), any());
        }

        /**
         * An unfinished file is one the session does not hold yet, and reads the same way: named
         * rather than quietly left out of the recording that comes back.
         */
        @Test
        void anUnfinishedFileIsRefusedRatherThanDropped() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(liveSession(
                    file("f-1", "profile-1.jfr", ManagedFile.JFR),
                    file("f-2", "profile-2.jfr", ManagedFile.JFR, CREATED_AT.plusSeconds(20))));

            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> manager.downloadRecordings(SESSION_ID, List.of("f-1", "f-2")));

            assertTrue(e.getMessage().contains("f-2"), e.getMessage());
            verify(streamClient, never()).streamFile(any(), any(), any());
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
            verify(streamClient, never()).streamFile(any(), any(), any());
            verify(recordingsManager, never()).createDownloadedRecording(any(), anyList(), anyList(), any());
        }

        @Test
        void aPickWithAChunkSkippedIsRefusedOnTheProgressPathToo() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(threeChunks());
            ProgressCallback progress = mock(ProgressCallback.class);

            assertThrows(IllegalArgumentException.class, () -> manager.downloadRecordingsWithProgress(
                    SESSION_ID, List.of("f-1", "f-3"), progress));

            verify(streamClient, never()).streamFile(any(), any(), any());
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
                    file("f-1", "recording.jfr", ManagedFile.JFR)));
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
