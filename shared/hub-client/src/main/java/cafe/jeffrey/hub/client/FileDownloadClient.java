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

import io.grpc.Context;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.api.v1.*;

import java.io.*;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Streams a recording session's files from a hub, one file per call. The hub serves every file
 * the same way and never merges; {@code RemoteRecordingsDownloadManager} stores a recording from
 * the chunks it streams through here.
 */
public class FileDownloadClient {

    @FunctionalInterface
    public interface InputStreamConsumer {
        void accept(InputStream inputStream, long contentLength) throws IOException;
    }

    private static final Logger LOG = LoggerFactory.getLogger(FileDownloadClient.class);

    private static final int PIPE_BUFFER_SIZE = 64 * 1024;

    /**
     * Content length reported to the consumer when the stream does not carry a total size.
     */
    private static final long UNKNOWN_CONTENT_LENGTH = -1;

    private final FileDownloadServiceGrpc.FileDownloadServiceBlockingStub stub;

    public FileDownloadClient(GrpcHubConnection connection) {
        this.stub = FileDownloadServiceGrpc.newBlockingStub(connection.getChannel());
    }

    /**
     * Streams one file straight into the consumer. The consumer decides where the bytes go; the
     * client itself never touches the disk.
     *
     * <p>The RPC runs under a cancellable context of its own, a child of the caller's, so that a
     * consumer giving up part-way (a cancelled download, a full disk) cuts the stream off rather
     * than leaving the hub sending chunks nobody reads. The caller's own deadline and cancellation
     * still reach the call through the parent.
     */
    public void streamFile(String sessionId, String fileId, InputStreamConsumer consumer) {
        Context.CancellableContext call = Context.current().withCancellation();
        try {
            Iterator<DataChunk> chunks = start(call, sessionId, fileId);
            streamChunksToConsumer(chunks, consumer, call);
        } finally {
            // A finished call ignores this; an abandoned one is released here rather than held
            // until the hub notices that nobody is reading.
            call.cancel(null);
        }
    }

    /**
     * Opens the call under the given context: a gRPC call is bound to the context that is current
     * when it is created, and that binding is what lets the context cancel it later.
     */
    private Iterator<DataChunk> start(Context.CancellableContext call, String sessionId, String fileId) {
        Context previous = call.attach();
        try {
            return stub.downloadFile(request(sessionId, fileId));
        } finally {
            call.detach(previous);
        }
    }

    private static DownloadFileRequest request(String sessionId, String fileId) {
        return DownloadFileRequest.newBuilder()
                .setSessionId(sessionId)
                .setFileId(fileId)
                .build();
    }

    /**
     * Streams gRPC data chunks through a PipedInputStream to the consumer.
     * The first chunk is fetched synchronously before the consumer starts — the server sends
     * the total size only on the first chunk, so this guarantees the consumer receives the
     * real content length instead of racing against the writer thread.
     * A virtual thread writes the remaining chunks to the pipe concurrently.
     *
     * <p>When the consumer fails, the read end of the pipe is closed <em>before</em> the writer is
     * joined, and the call is cancelled: a writer parked on a full pipe only wakes when the read
     * end goes away, and one parked on the hub only wakes when the call does. Joining first, as
     * this once did, left both threads waiting on each other for as long as the file was larger
     * than the pipe.
     */
    private static void streamChunksToConsumer(
            Iterator<DataChunk> chunks, InputStreamConsumer consumer, Context.CancellableContext call) {

        DataChunk firstChunk;
        try {
            firstChunk = chunks.hasNext() ? chunks.next() : null;
        } catch (StatusRuntimeException e) {
            throw toRuntimeException(e);
        }

        long contentLength = (firstChunk != null && firstChunk.getTotalSize() > 0)
                ? firstChunk.getTotalSize()
                : UNKNOWN_CONTENT_LENGTH;

        PipedOutputStream pipeOut = new PipedOutputStream();
        PipedInputStream pipeIn = connect(pipeOut);
        AtomicReference<Throwable> writerError = new AtomicReference<>();

        Thread writer = Thread.ofVirtual().start(() -> {
            try (pipeOut) {
                if (firstChunk != null) {
                    firstChunk.getData().writeTo(pipeOut);
                }
                while (chunks.hasNext()) {
                    chunks.next().getData().writeTo(pipeOut);
                }
            } catch (Exception e) {
                writerError.set(e);
            }
        });

        try {
            consumer.accept(pipeIn, contentLength);
        } catch (IOException e) {
            call.cancel(e);
            throw new RuntimeException("Failed to stream gRPC data chunks", e);
        } catch (RuntimeException e) {
            // The consumer's own reason, unwrapped: a cancellation must arrive as one.
            call.cancel(e);
            throw e;
        } finally {
            closeQuietly(pipeIn);
            join(writer);
        }

        Throwable failure = writerError.get();
        if (failure != null) {
            LOG.error("Error writing gRPC chunks to pipe", failure);
            throw toRuntimeException(failure);
        }
    }

    private static PipedInputStream connect(PipedOutputStream pipeOut) {
        try {
            return new PipedInputStream(pipeOut, PIPE_BUFFER_SIZE);
        } catch (IOException e) {
            throw new RuntimeException("Failed to connect the download pipe", e);
        }
    }

    private static void closeQuietly(InputStream stream) {
        try {
            stream.close();
        } catch (IOException e) {
            LOG.debug("Closing the download pipe failed: reason={}", e.getMessage());
        }
    }

    private static void join(Thread writer) {
        try {
            writer.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for the download to stop", e);
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
