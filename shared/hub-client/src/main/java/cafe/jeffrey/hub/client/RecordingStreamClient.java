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

package cafe.jeffrey.hub.client;

import cafe.jeffrey.microscope.grpc.client.*;

import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import cafe.jeffrey.hub.api.v1.*;
import cafe.jeffrey.shared.common.Schedulers;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class RecordingStreamClient {

    @FunctionalInterface
    public interface InputStreamConsumer {
        void accept(InputStream inputStream, long contentLength) throws IOException;
    }

    private static final Logger LOG = LoggerFactory.getLogger(RecordingStreamClient.class);

    private static final int PIPE_BUFFER_SIZE = 64 * 1024;

    /**
     * Content length reported to the consumer when the stream does not carry a total size.
     */
    private static final long UNKNOWN_CONTENT_LENGTH = -1;

    private final RecordingDownloadServiceGrpc.RecordingDownloadServiceBlockingStub stub;

    public RecordingStreamClient(GrpcHubConnection connection) {
        this.stub = RecordingDownloadServiceGrpc.newBlockingStub(connection.getChannel());
    }

    public CompletableFuture<Resource> downloadRecordings(
            String sessionId, List<String> recordingIds) {

        return CompletableFuture.supplyAsync(() -> {
            DownloadMergedRecordingsRequest request = DownloadMergedRecordingsRequest.newBuilder()
                    .setSessionId(sessionId)
                    .addAllFileIds(recordingIds)
                    .build();

            Iterator<DataChunk> chunks = stub.downloadMergedRecordings(request);
            return collectChunksToResource(chunks);
        }, Schedulers.sharedVirtual());
    }

    public CompletableFuture<Resource> downloadArtifactFile(
            String sessionId, String fileId) {

        return CompletableFuture.supplyAsync(() -> {
            DownloadArtifactFileRequest request = DownloadArtifactFileRequest.newBuilder()
                    .setSessionId(sessionId)
                    .setFileId(fileId)
                    .build();

            Iterator<DataChunk> chunks = stub.downloadArtifactFile(request);
            return collectChunksToResource(chunks);
        }, Schedulers.sharedVirtual());
    }

    public void streamRecordings(
            String sessionId, List<String> recordingIds, InputStreamConsumer consumer) {

        DownloadMergedRecordingsRequest request = DownloadMergedRecordingsRequest.newBuilder()
                .setSessionId(sessionId)
                .addAllFileIds(recordingIds)
                .build();

        Iterator<DataChunk> chunks = stub.downloadMergedRecordings(request);
        streamChunksToConsumer(chunks, consumer);
    }

    public void streamArtifactFile(
            String sessionId, String fileId, InputStreamConsumer consumer) {

        DownloadArtifactFileRequest request = DownloadArtifactFileRequest.newBuilder()
                .setSessionId(sessionId)
                .setFileId(fileId)
                .build();

        Iterator<DataChunk> chunks = stub.downloadArtifactFile(request);
        streamChunksToConsumer(chunks, consumer);
    }

    public void streamRecordingFile(
            String sessionId, String fileId, InputStreamConsumer consumer) {

        DownloadRecordingFileRequest request = DownloadRecordingFileRequest.newBuilder()
                .setSessionId(sessionId)
                .setFileId(fileId)
                .build();

        Iterator<DataChunk> chunks = stub.downloadRecordingFile(request);
        streamChunksToConsumer(chunks, consumer);
    }

    /**
     * Collects gRPC data chunks into a temporary file and returns it as a Spring Resource.
     */
    private static Resource collectChunksToResource(Iterator<DataChunk> chunks) {
        try {
            Path tempFile = Files.createTempFile("grpc-download-", ".tmp");

            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(tempFile))) {
                while (chunks.hasNext()) {
                    chunks.next().getData().writeTo(out);
                }
            }

            return new FileSystemResource(tempFile);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to collect gRPC data chunks to temp file", e);
        }
    }

    /**
     * Streams gRPC data chunks through a PipedInputStream to the consumer.
     * The first chunk is fetched synchronously before the consumer starts — the server sends
     * the total size only on the first chunk, so this guarantees the consumer receives the
     * real content length instead of racing against the writer thread.
     * A virtual thread writes the remaining chunks to the pipe concurrently.
     */
    private static void streamChunksToConsumer(Iterator<DataChunk> chunks, InputStreamConsumer consumer) {
        DataChunk firstChunk;
        try {
            firstChunk = chunks.hasNext() ? chunks.next() : null;
        } catch (StatusRuntimeException e) {
            throw toRuntimeException(e);
        }

        long contentLength = (firstChunk != null && firstChunk.getTotalSize() > 0)
                ? firstChunk.getTotalSize()
                : UNKNOWN_CONTENT_LENGTH;

        try {
            PipedOutputStream pipeOut = new PipedOutputStream();
            PipedInputStream pipeIn = new PipedInputStream(pipeOut, PIPE_BUFFER_SIZE);

            Throwable[] writerError = {null};

            Thread writer = Thread.ofVirtual().start(() -> {
                try (pipeOut) {
                    if (firstChunk != null) {
                        firstChunk.getData().writeTo(pipeOut);
                    }
                    while (chunks.hasNext()) {
                        chunks.next().getData().writeTo(pipeOut);
                    }
                } catch (Exception e) {
                    writerError[0] = e;
                    LOG.error("Error writing gRPC chunks to pipe", e);
                }
            });

            try {
                consumer.accept(pipeIn, contentLength);
            } finally {
                writer.join();
                pipeIn.close();
            }

            if (writerError[0] != null) {
                throw toRuntimeException(writerError[0]);
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Failed to stream gRPC data chunks", e);
        }
    }

    /**
     * Maps a streaming failure to a RuntimeException, preferring the gRPC status description
     * as the message when available.
     */
    private static RuntimeException toRuntimeException(Throwable error) {
        String message = error.getMessage();
        if (error instanceof StatusRuntimeException sre) {
            String description = sre.getStatus().getDescription();
            message = description != null ? description : message;
        }
        return new RuntimeException(message, error);
    }
}
