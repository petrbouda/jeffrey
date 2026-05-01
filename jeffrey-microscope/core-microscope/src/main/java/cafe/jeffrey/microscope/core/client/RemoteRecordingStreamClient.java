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

package cafe.jeffrey.microscope.core.client;

import cafe.jeffrey.microscope.grpc.client.*;

import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import cafe.jeffrey.server.api.v1.*;
import cafe.jeffrey.shared.common.Schedulers;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class RemoteRecordingStreamClient {

    @FunctionalInterface
    public interface InputStreamConsumer {
        void accept(InputStream inputStream, long contentLength) throws IOException;
    }

    private static final Logger LOG = LoggerFactory.getLogger(RemoteRecordingStreamClient.class);

    private final RecordingDownloadServiceGrpc.RecordingDownloadServiceBlockingStub stub;

    public RemoteRecordingStreamClient(GrpcServerConnection connection) {
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
            long totalSize = -1;

            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(tempFile))) {
                while (chunks.hasNext()) {
                    DataChunk chunk = chunks.next();
                    chunk.getData().writeTo(out);
                    if (totalSize == -1 && chunk.getTotalSize() > 0) {
                        totalSize = chunk.getTotalSize();
                    }
                }
            }

            return new FileSystemResource(tempFile);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to collect gRPC data chunks to temp file", e);
        }
    }

    /**
     * Streams gRPC data chunks through a PipedInputStream to the consumer.
     * Uses a virtual thread to write chunks to the pipe concurrently.
     */
    private static void streamChunksToConsumer(Iterator<DataChunk> chunks, InputStreamConsumer consumer) {
        try {
            PipedOutputStream pipeOut = new PipedOutputStream();
            PipedInputStream pipeIn = new PipedInputStream(pipeOut, 64 * 1024);

            // Determine total size from first chunk
            long[] totalSize = {-1};
            Throwable[] writerError = {null};

            Thread writer = Thread.ofVirtual().start(() -> {
                try (pipeOut) {
                    while (chunks.hasNext()) {
                        DataChunk chunk = chunks.next();
                        if (totalSize[0] == -1 && chunk.getTotalSize() > 0) {
                            totalSize[0] = chunk.getTotalSize();
                        }
                        chunk.getData().writeTo(pipeOut);
                    }
                } catch (Exception e) {
                    writerError[0] = e;
                    LOG.error("Error writing gRPC chunks to pipe", e);
                }
            });

            try {
                consumer.accept(pipeIn, totalSize[0]);
            } finally {
                writer.join();
                pipeIn.close();
            }

            if (writerError[0] != null) {
                String message = writerError[0].getMessage();
                if (writerError[0] instanceof StatusRuntimeException sre) {
                    String description = sre.getStatus().getDescription();
                    message = description != null ? description : message;
                }
                throw new RuntimeException(message, writerError[0]);
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Failed to stream gRPC data chunks", e);
        }
    }
}
