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

package cafe.jeffrey.hub.core.grpc;

import com.google.protobuf.ByteString;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.api.v1.DataChunk;
import cafe.jeffrey.hub.api.v1.DownloadFileRequest;
import cafe.jeffrey.hub.api.v1.FileDownloadServiceGrpc;
import cafe.jeffrey.hub.core.manager.RepositoryManager;
import cafe.jeffrey.hub.core.project.repository.FileVanishedException;
import cafe.jeffrey.shared.common.Schedulers;
import cafe.jeffrey.shared.common.filesystem.FileSizeReader;
import cafe.jeffrey.shared.common.model.repository.StreamedFile;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.NoSuchFileException;

public class FileDownloadGrpcService extends FileDownloadServiceGrpc.FileDownloadServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(FileDownloadGrpcService.class);
    private static final int CHUNK_SIZE = 64 * 1024; // 64KB

    private final GrpcLookups lookups;

    public FileDownloadGrpcService(GrpcLookups lookups) {
        this.lookups = lookups;
    }

    /**
     * Streams one file of a session, whatever that file is.
     *
     * <p>The gate is attached here, on the gRPC handler thread and before this method returns:
     * gRPC rejects {@code setOnReadyHandler} / {@code setOnCancelHandler} once the observer has
     * been handed back. Everything after it runs on the streaming executor.
     */
    @Override
    public void downloadFile(DownloadFileRequest request, StreamObserver<DataChunk> responseObserver) {
        ServerCallStreamObserver<DataChunk> observer = (ServerCallStreamObserver<DataChunk>) responseObserver;
        ReadyGate gate = ReadyGate.attach(observer);

        Schedulers.streamingExecutor().execute(() -> {
            try {
                RepositoryManager repoManager = lookups.repositoryManagerForSession(request.getSessionId());

                LOG.debug("Streaming file via gRPC: sessionId={} fileId={}",
                        request.getSessionId(), request.getFileId());

                try (OpenFile file = open(repoManager, request.getSessionId(), request.getFileId())) {
                    streamWithBackpressure(file, observer, gate);
                }
            } catch (StatusRuntimeException e) {
                // A lookup that already knows its status — NOT_FOUND for a session or file that is
                // not there — passes through as it is.
                observer.onError(e);
            } catch (IllegalArgumentException e) {
                // Everything the lookup refuses: a file the session does not hold, a transient
                // one, one still being written, one that is empty. Each is a statement about what
                // was asked for, so it travels as INVALID_ARGUMENT carrying its own sentence
                // rather than as a hub failure.
                LOG.debug("Refusing to stream a file: sessionId={} fileId={} reason={}",
                        request.getSessionId(), request.getFileId(), e.getMessage());
                observer.onError(GrpcExceptions.invalidArgument(e.getMessage()));
            } catch (Exception e) {
                LOG.error("Failed to stream file: sessionId={} fileId={}",
                        request.getSessionId(), request.getFileId(), e);
                observer.onError(GrpcExceptions.internal(e));
            }
        });
    }

    /**
     * Resolves the id and opens what it names, once more when the first answer was gone before a
     * byte of it could be read.
     *
     * <p>That is the compression job and nothing else: it publishes an archive and removes the
     * file it was made from, and a request that resolved that file a moment earlier is left
     * holding a path to something no longer there. The id survives the rewrite, so asking again
     * names the archive, and the name travelling on the first chunk tells the reader which of the
     * two it is getting. Once only: a second miss is a file that is genuinely not there.
     *
     * <p>Before anything is sent, so the retry is invisible to the caller. Once the first chunk
     * is on its way there is no going back, and nothing can take the file away from an open
     * handle anyway.
     */
    private static OpenFile open(RepositoryManager repoManager, String sessionId, String fileId)
            throws IOException {

        try {
            return OpenFile.of(repoManager.streamFile(sessionId, fileId));
        } catch (IOException | RuntimeException e) {
            if (!replacedWhileResolving(e)) {
                throw e;
            }
            LOG.debug("The file this id named was replaced before it could be opened, resolving it again: "
                    + "sessionId={} fileId={}", sessionId, fileId);
            return OpenFile.of(repoManager.streamFile(sessionId, fileId));
        }
    }

    /**
     * Whether a failure means "the file the id named is no longer there", which can arrive two
     * ways: the listing's own check caught it, or the open did. The second is wrapped by the
     * size reader, so the question is asked of the whole cause chain rather than of the
     * exception in hand.
     */
    private static boolean replacedWhileResolving(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof FileVanishedException || cause instanceof NoSuchFileException) {
                return true;
            }
        }
        return false;
    }

    /**
     * A file's bytes together with everything the first chunk has to say about them, read at the
     * one moment the file is known to be there. Taken separately, the size and the stream are two
     * chances for the compression job to get in between.
     */
    private record OpenFile(String name, long totalSize, InputStream stream) implements Closeable {

        static OpenFile of(StreamedFile file) throws IOException {
            long totalSize = FileSizeReader.OPEN_HANDLE.size(file.path());
            return new OpenFile(file.fileName(), totalSize, file.openStream());
        }

        @Override
        public void close() throws IOException {
            stream.close();
        }
    }

    /**
     * What the first chunk says about the whole transfer: how many bytes are coming, and the name
     * the file has here <em>now</em>. The caller listed the session some time ago and the
     * compression job may have renamed the file since; written under the name it asked for, the
     * file would claim to be something it is not, and every reader downstream goes by the name.
     */
    private static DataChunk.Builder header(DataChunk.Builder builder, OpenFile file) {
        return builder
                .setTotalSize(file.totalSize())
                .setFilename(file.name());
    }

    private static void streamWithBackpressure(
            OpenFile file,
            ServerCallStreamObserver<DataChunk> observer,
            ReadyGate gate) throws IOException, InterruptedException {

        boolean firstChunk = true;

        byte[] buffer = new byte[CHUNK_SIZE];
        int bytesRead;
        while (!gate.isCancelled() && (bytesRead = file.stream().read(buffer)) != -1) {
            gate.awaitReady();
            if (gate.isCancelled()) {
                return;
            }

            DataChunk.Builder builder = DataChunk.newBuilder()
                    .setData(ByteString.copyFrom(buffer, 0, bytesRead));
            if (firstChunk) {
                header(builder, file);
                firstChunk = false;
            }
            observer.onNext(builder.build());
        }

        if (gate.isCancelled()) {
            return;
        }

        // A file with no bytes still has to say what it is. Without this the reader is handed a
        // stream with no name and no size, and has to guess both from what it asked for.
        if (firstChunk) {
            observer.onNext(header(DataChunk.newBuilder(), file).build());
        }

        observer.onCompleted();
    }
}
