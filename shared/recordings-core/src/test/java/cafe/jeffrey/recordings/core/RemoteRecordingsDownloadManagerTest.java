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
import cafe.jeffrey.recordings.core.download.ProgressCallback;
import cafe.jeffrey.recordings.core.manager.RecordingsCoreManager;
import cafe.jeffrey.shared.common.filesystem.TempDirectory;
import cafe.jeffrey.shared.common.model.repository.RecordingStatus;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;
import cafe.jeffrey.shared.common.model.repository.ChunkWindow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
                tempDirProvider, streamClient, repositoryClient, recordingsManager, originContext, "checkout");

        when(recordingsManager.createDownloadedRecording(any(), any(), anyList(), any()))
                .thenReturn(RECORDING_ID);
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

    /**
     * A downloaded file as the stream client hands it over. {@code ByteArrayResource} reports no
     * filename on its own, and the download code names its temp file from that.
     */
    private static Resource resource(String filename) {
        return new ByteArrayResource("recording-bytes".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }

    @Nested
    class MergeAndDownloadSession {

        @Test
        void returnsTheIdOfTheRecordingItCreated() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(session(
                    file("f-1", "recording.jfr", SupportedRecordingFile.JFR, RecordingStatus.FINISHED)));
            when(streamClient.downloadRecordings(eq(SESSION_ID), anyList()))
                    .thenReturn(CompletableFuture.completedFuture(resource("recording.jfr")));

            String recordingId = manager.mergeAndDownloadSession(SESSION_ID);

            assertEquals(RECORDING_ID, recordingId);
        }

        @Test
        void tagsTheRecordingWithTheUpstreamSessionId() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(session(
                    file("f-1", "recording.jfr", SupportedRecordingFile.JFR, RecordingStatus.FINISHED)));
            when(streamClient.downloadRecordings(eq(SESSION_ID), anyList()))
                    .thenReturn(CompletableFuture.completedFuture(resource("recording.jfr")));

            manager.mergeAndDownloadSession(SESSION_ID);

            Map<String, String> expectedTags = originContext.toTagMap(SESSION_ID);
            verify(recordingsManager).createDownloadedRecording(
                    eq(SESSION_ID), any(), anyList(), eq(expectedTags));
        }
    }

    @Nested
    class MergeAndDownloadRecordings {

        @Test
        void returnsTheIdOfTheRecordingItCreated() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(session(
                    file("f-1", "recording.jfr", SupportedRecordingFile.JFR, RecordingStatus.FINISHED),
                    file("f-2", "heap.hprof", SupportedRecordingFile.HEAP_DUMP, RecordingStatus.FINISHED)));
            when(streamClient.downloadRecordings(eq(SESSION_ID), anyList()))
                    .thenReturn(CompletableFuture.completedFuture(resource("recording.jfr")));
            when(streamClient.downloadArtifactFile(SESSION_ID, "f-2"))
                    .thenReturn(CompletableFuture.completedFuture(resource("heap.hprof")));

            String recordingId = manager.mergeAndDownloadRecordings(SESSION_ID, List.of("f-1", "f-2"));

            assertEquals(RECORDING_ID, recordingId);
        }
    }

    /**
     * The UI's "download session" names every file, and that recording is the session; a pick of
     * some recording files is a part of it and carries the window tag like a window does.
     */
    @Nested
    class MergeAndDownloadPart {

        private static final Instant SESSION_END = CREATED_AT.plusSeconds(60);

        private RecordingSessionResponse threeChunks() {
            return session(
                    file("f-1", "profile-1.jfr", SupportedRecordingFile.JFR, RecordingStatus.FINISHED, CREATED_AT),
                    file("f-2", "profile-2.jfr", SupportedRecordingFile.JFR, RecordingStatus.FINISHED, CREATED_AT.plusSeconds(20)),
                    file("f-3", "profile-3.jfr", SupportedRecordingFile.JFR, RecordingStatus.FINISHED, CREATED_AT.plusSeconds(40)),
                    file("f-4", "heap.hprof", SupportedRecordingFile.HEAP_DUMP, RecordingStatus.FINISHED));
        }

        /**
         * Named the way {@code RecordingStreamClient} really names it: the merged stream is
         * collected into {@code Files.createTempFile(dir, "grpc-download-", ".tmp")}, so the
         * manager is handed a temp file and has to name the recording itself.
         */
        private void merges() {
            when(streamClient.downloadRecordings(eq(SESSION_ID), anyList()))
                    .thenReturn(CompletableFuture.completedFuture(resource("grpc-download-12345.tmp")));
        }

        @Test
        void aWindowMergesOnlyTheCoveringFilesAndNothingElse() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(threeChunks());
            merges();

            String recordingId = manager.mergeAndDownloadWindow(SESSION_ID,
                    new ChunkWindow(CREATED_AT.plusSeconds(25), CREATED_AT.plusSeconds(35)));

            assertEquals(RECORDING_ID, recordingId);
            verify(streamClient).downloadRecordings(SESSION_ID, List.of("f-2"));
            verify(streamClient, never()).downloadArtifactFile(any(), any());
        }

        @Test
        void aWindowIsTaggedAndNamedAfterTheSpanItsFilesCover() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(threeChunks());
            merges();
            ArgumentCaptor<Path> recordingPath = ArgumentCaptor.forClass(Path.class);

            manager.mergeAndDownloadWindow(SESSION_ID, new ChunkWindow(CREATED_AT.plusSeconds(45), null));

            Map<String, String> expectedTags = new LinkedHashMap<>(originContext.toTagMap(SESSION_ID));
            expectedTags.put(OriginContext.TAG_WINDOW,
                    CREATED_AT.plusSeconds(40).toEpochMilli() + "-" + SESSION_END.toEpochMilli());
            verify(recordingsManager).createDownloadedRecording(
                    eq(SESSION_ID), recordingPath.capture(), anyList(), eq(expectedTags));
            assertEquals("checkout_2026-03-01T12-00-00Z_2026-03-01T12-00-40Z_2026-03-01T12-01-00Z.jfr.lz4",
                    recordingPath.getValue().getFileName().toString());
        }

        @Test
        void aWindowNoFileCoversIsRefused() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(threeChunks());

            assertThrows(IllegalArgumentException.class, () -> manager.mergeAndDownloadWindow(SESSION_ID,
                    new ChunkWindow(SESSION_END.plusSeconds(600), SESSION_END.plusSeconds(1200))));
            verify(recordingsManager, never()).createDownloadedRecording(any(), any(), anyList(), any());
        }

        @Test
        void aWholeSessionIsNamedAfterTheProjectAndTheSessionStart() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(threeChunks());
            merges();
            when(streamClient.downloadArtifactFile(SESSION_ID, "f-4"))
                    .thenReturn(CompletableFuture.completedFuture(resource("heap.hprof")));
            ArgumentCaptor<Path> recordingPath = ArgumentCaptor.forClass(Path.class);

            manager.mergeAndDownloadSession(SESSION_ID);

            verify(recordingsManager).createDownloadedRecording(
                    eq(SESSION_ID), recordingPath.capture(), anyList(), any());
            assertEquals("checkout_2026-03-01T12-00-00Z.jfr.lz4",
                    recordingPath.getValue().getFileName().toString());
        }

        /**
         * A merge concatenates the chunks, so a skipped one leaves no trace in the result. Refused
         * before the transfer rather than after it.
         */
        @Test
        void aPickWithAChunkSkippedIsRefusedBeforeAnythingIsTransferred() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(threeChunks());

            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> manager.mergeAndDownloadRecordings(SESSION_ID, List.of("f-1", "f-3")));

            assertTrue(e.getMessage().contains("profile-2.jfr"), e.getMessage());
            verify(streamClient, never()).downloadRecordings(any(), anyList());
            verify(recordingsManager, never()).createDownloadedRecording(any(), any(), anyList(), any());
        }

        @Test
        void aPickWithAChunkSkippedIsRefusedOnTheProgressPathToo() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(threeChunks());
            ProgressCallback progress = mock(ProgressCallback.class);

            assertThrows(IllegalArgumentException.class, () -> manager.mergeAndDownloadRecordingsWithProgress(
                    SESSION_ID, List.of("f-1", "f-3"), progress));

            verify(streamClient, never()).downloadRecordings(any(), anyList());
            verify(progress, never()).onStart(anyInt(), anyLong());
        }

        @Test
        void anUnbrokenRunOfChunksIsAccepted() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(threeChunks());
            merges();

            assertEquals(RECORDING_ID, manager.mergeAndDownloadRecordings(SESSION_ID, List.of("f-1", "f-2")));
        }

        @Test
        void namingEveryFileIsTheWholeSessionAndNamingSomeIsAPart() {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(threeChunks());
            merges();
            when(streamClient.downloadArtifactFile(SESSION_ID, "f-4"))
                    .thenReturn(CompletableFuture.completedFuture(resource("heap.hprof")));
            ArgumentCaptor<Map<String, String>> tags = ArgumentCaptor.forClass(Map.class);

            manager.mergeAndDownloadRecordings(SESSION_ID, List.of("f-1", "f-2", "f-3", "f-4"));
            manager.mergeAndDownloadRecordings(SESSION_ID, List.of("f-2", "f-4"));

            verify(recordingsManager, times(2)).createDownloadedRecording(eq(SESSION_ID), any(), anyList(), tags.capture());
            assertFalse(tags.getAllValues().get(0).containsKey(OriginContext.TAG_WINDOW));
            assertEquals(CREATED_AT.plusSeconds(20).toEpochMilli() + "-" + CREATED_AT.plusSeconds(40).toEpochMilli(),
                    tags.getAllValues().get(1).get(OriginContext.TAG_WINDOW));
        }
    }

    @Nested
    class MergeAndDownloadRecordingsWithProgress {

        @Test
        void returnsTheIdOfTheRecordingItCreated() throws IOException {
            when(repositoryClient.recordingSession(SESSION_ID)).thenReturn(session(
                    file("f-1", "recording.jfr", SupportedRecordingFile.JFR, RecordingStatus.FINISHED)));
            doAnswer(invocation -> {
                RecordingStreamClient.InputStreamConsumer consumer = invocation.getArgument(2);
                try (InputStream in = resource("recording.jfr").getInputStream()) {
                    consumer.accept(in, 15L);
                }
                return null;
            }).when(streamClient).streamRecordings(eq(SESSION_ID), anyList(), any());

            ProgressCallback progress = mock(ProgressCallback.class);

            String recordingId = manager.mergeAndDownloadRecordingsWithProgress(
                    SESSION_ID, List.of("f-1"), progress);

            assertEquals(RECORDING_ID, recordingId);
            verify(progress).onComplete();
        }
    }
}
