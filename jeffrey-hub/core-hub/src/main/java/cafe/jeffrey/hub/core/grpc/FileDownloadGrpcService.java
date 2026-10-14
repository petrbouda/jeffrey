/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import cafe.jeffrey.hub.core.project.repository.FileLookup;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Executor;

public class FileDownloadGrpcService extends FileDownloadServiceGrpc.FileDownloadServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(FileDownloadGrpcService.class);
    private static final int CHUNK_SIZE = 64 * 1024; // 64KB

    private final GrpcLookups lookups;
    private final Executor streamingExecutor;

    /**
     * @param streamingExecutor where the bytes are read and pushed, off the gRPC handler thread
     */
    public FileDownloadGrpcService(GrpcLookups lookups, Executor streamingExecutor) {
        this.lookups = lookups;
        this.streamingExecutor = streamingExecutor;
    }

    /**
     * Streams one file of a session, whatever that file is.
     *
     * <p>The gate is attached on the gRPC handler thread, before this method returns: gRPC rejects
     * {@code setOnReadyHandler} / {@code setOnCancelHandler} after that. Everything else runs on the
     * streaming executor.
     */
    @Override
    public void downloadFile(DownloadFileRequest request, StreamObserver<DataChunk> responseObserver) {
        ServerCallStreamObserver<DataChunk> observer = (ServerCallStreamObserver<DataChunk>) responseObserver;
        ReadyGate gate = ReadyGate.attach(observer);
        String sessionId = request.getSessionId();
        String fileId = request.getFileId();

        streamingExecutor.execute(() -> {
            try {
                RepositoryManager repoManager = lookups.repositoryManagerForSession(sessionId);
                LOG.debug("Streaming file via gRPC: session_id={} file_id={}", sessionId, fileId);

                switch (open(repoManager, sessionId, fileId)) {
                    case OpenFile file -> {
                        try (file) {
                            streamWithBackpressure(file, observer, gate);
                        }
                    }
                    case Refusal(FileLookup.Refused refused) -> {
                        LOG.debug("Refusing to stream a file: session_id={} file_id={} reason={}",
                                sessionId, fileId, refused.reason());
                        observer.onError(GrpcExceptions.invalidArgument(refused.reason()));
                    }
                }
            } catch (StatusRuntimeException e) {
                // NOT_FOUND for a session that is not there, already mapped by the lookup
                observer.onError(e);
            } catch (Exception e) {
                LOG.error("Failed to stream file: session_id={} file_id={}", sessionId, fileId, e);
                observer.onError(GrpcExceptions.internal(e));
            }
        });
    }

    /**
     * Looks the id up and opens what it names, once more if the file was gone before it could be
     * opened: the compression job replaced it with its archive, which the same id names. Only once,
     * and before anything is sent, so the retry is invisible to the caller.
     */
    private static Opened open(RepositoryManager repoManager, String sessionId, String fileId) throws IOException {
        Opened first = attempt(repoManager, sessionId, fileId);
        if (first instanceof Refusal(FileLookup.Vanished _)) {
            LOG.debug("File was replaced before it could be opened, looking it up again: session_id={} file_id={}",
                    sessionId, fileId);
            return attempt(repoManager, sessionId, fileId);
        }
        return first;
    }

    private static Opened attempt(RepositoryManager repoManager, String sessionId, String fileId) throws IOException {
        return switch (repoManager.file(sessionId, fileId)) {
            case FileLookup.Found(Path path) -> OpenFile.of(sessionId, path);
            case FileLookup.Refused refused -> new Refusal(refused);
        };
    }

    /** A looked-up file: open and ready to stream, or refused with its reason. */
    private sealed interface Opened permits OpenFile, Refusal {
    }

    private record Refusal(FileLookup.Refused refused) implements Opened {
    }

    /**
     * A file's bytes with the name and size the first chunk carries, all taken from one open
     * handle so the compression job cannot slip in between them.
     */
    private record OpenFile(String name, long totalSize, InputStream stream) implements Opened, Closeable {

        /** Opens the file, or reports it {@link FileLookup.Vanished} when it is gone by now. */
        static Opened of(String sessionId, Path path) throws IOException {
            String name = path.getFileName().toString();
            FileChannel channel;
            try {
                channel = FileChannel.open(path, StandardOpenOption.READ);
            } catch (NoSuchFileException e) {
                return new Refusal(new FileLookup.Vanished(sessionId, name));
            }
            try {
                return new OpenFile(name, channel.size(), Channels.newInputStream(channel));
            } catch (IOException e) {
                channel.close();
                throw e;
            }
        }

        @Override
        public void close() throws IOException {
            stream.close();
        }
    }

    /**
     * What the first chunk says about the whole transfer: the byte count and the name the file has
     * here now, which differs from the listed one when the compression job renamed it since.
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

        // An empty file still sends one chunk carrying its name and size
        if (firstChunk) {
            observer.onNext(header(DataChunk.newBuilder(), file).build());
        }

        observer.onCompleted();
    }
}
