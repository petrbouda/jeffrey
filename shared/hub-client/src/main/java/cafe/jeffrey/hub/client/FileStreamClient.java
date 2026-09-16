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
import cafe.jeffrey.hub.api.v1.*;

import java.io.*;
import java.nio.file.Path;
import java.util.Iterator;

public class FileStreamClient {

    @FunctionalInterface
    public interface InputStreamConsumer {
        void accept(InputStream inputStream, TransferredFile file) throws IOException;
    }

    /**
     * What the hub said it was sending, before a byte of it is written.
     *
     * @param name the name the file has on the hub — which is the name the receiver writes, not
     *             the one it asked for. A caller's listing is older than its request, and the
     *             compression job may have replaced {@code x.jfr} with {@code x.jfr.lz4} since.
     *             Written under the remembered name, an LZ4 frame sits in a file called
     *             {@code .jfr}, and compression is recognised by the extension — nothing sniffs
     *             the frame — so every reader treats it as a raw recording and fails on the
     *             chunk magic.
     * @param size the file's size, or {@link #UNKNOWN_CONTENT_LENGTH} when the hub sent none
     */
    public record TransferredFile(String name, long size) {

        /**
         * Reduced to a single path element here rather than at each place that resolves it into a
         * directory: the name comes off the wire, and one guard that cannot be forgotten beats
         * three that can.
         */
        public TransferredFile {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException(
                        "The hub sent a file with no name; there is nothing to write it as.");
            }
            name = Path.of(name).getFileName().toString();
            if (name.isBlank() || name.equals(".") || name.equals("..")) {
                throw new IllegalArgumentException(
                        "Refusing a transferred file name that is not a single path element");
            }
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(FileStreamClient.class);

    private static final int PIPE_BUFFER_SIZE = 64 * 1024;

    /**
     * Content length reported to the consumer when the stream does not carry a total size.
     */
    private static final long UNKNOWN_CONTENT_LENGTH = -1;

    private final FileDownloadServiceGrpc.FileDownloadServiceBlockingStub stub;

    public FileStreamClient(GrpcHubConnection connection) {
        this.stub = FileDownloadServiceGrpc.newBlockingStub(connection.getChannel());
    }

    /**
     * Pulls one file of a session onto this machine, whatever kind it is.
     *
     * <p>One call for every kind. There were two, and they differed only in the RPC they named:
     * the hub looked the file up, checked its category and streamed the bytes either way. What
     * the category decides is what the caller does with the file afterwards, and the caller read
     * it off the listing that gave it this id.
     */
    public void streamFile(String sessionId, String fileId, InputStreamConsumer consumer) {
        DownloadFileRequest request = DownloadFileRequest.newBuilder()
                .setSessionId(sessionId)
                .setFileId(fileId)
                .build();

        Iterator<DataChunk> chunks = stub.downloadFile(request);
        streamChunksToConsumer(chunks, consumer);
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
        TransferredFile transferred = new TransferredFile(
                firstChunk != null ? firstChunk.getFilename() : "", contentLength);

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
                consumer.accept(pipeIn, transferred);
            } finally {
                // Closed before the join, not after it. A consumer that stopped early -- a
                // cancelled download -- leaves the writer blocked on a full pipe with nobody
                // reading, and PipedInputStream only gives up on a reader it can see is dead:
                // this thread is alive, waiting in that very join. Closing first turns the
                // writer's next write into the IOException it handles.
                pipeIn.close();
                writer.join();
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
