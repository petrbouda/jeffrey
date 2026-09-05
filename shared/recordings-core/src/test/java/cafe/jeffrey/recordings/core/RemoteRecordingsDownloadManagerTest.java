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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
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
        return new RepositoryFileResponse(
                id, name, CREATED_AT.toEpochMilli(), 1024L, type, status, type == SupportedRecordingFile.JFR);
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
