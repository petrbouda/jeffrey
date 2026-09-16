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
import cafe.jeffrey.shared.common.Schedulers;
import cafe.jeffrey.shared.common.filesystem.FileSizeReader;
import cafe.jeffrey.shared.common.model.repository.StreamedFile;

import java.io.IOException;
import java.io.InputStream;

public class FileDownloadGrpcService extends FileDownloadServiceGrpc.FileDownloadServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(FileDownloadGrpcService.class);
    private static final int CHUNK_SIZE = 64 * 1024; // 64KB

    private final GrpcLookups lookups;

    public FileDownloadGrpcService(GrpcLookups lookups) {
        this.lookups = lookups;
    }

    /**
     * Streams one file of a session, whatever kind it is.
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

                streamWithBackpressure(
                        repoManager.streamFile(request.getSessionId(), request.getFileId()), observer, gate);
            } catch (StatusRuntimeException e) {
                // A lookup that already knows its status — NOT_FOUND for a session or file that is
                // not there — passes through as it is. Anything else is this server's fault.
                observer.onError(e);
            } catch (Exception e) {
                LOG.error("Failed to stream file: sessionId={} fileId={}",
                        request.getSessionId(), request.getFileId(), e);
                observer.onError(GrpcExceptions.internal(e));
            }
        });
    }

    /**
     * What the first chunk says about the whole transfer: how many bytes are coming, and the name
     * the file has here <em>now</em>. The caller listed the session some time ago and the
     * compression job may have renamed the file since; written under the name it asked for, an
     * archive is read as a plain recording and fails on the chunk magic.
     */
    private static DataChunk.Builder header(
            DataChunk.Builder builder, StreamedFile file, long totalSize) {

        return builder
                .setTotalSize(totalSize)
                .setFilename(file.fileName());
    }

    private static void streamWithBackpressure(
            StreamedFile file,
            ServerCallStreamObserver<DataChunk> observer,
            ReadyGate gate) throws IOException, InterruptedException {

        long totalSize = FileSizeReader.OPEN_HANDLE.size(file.path());
        boolean firstChunk = true;

        try (InputStream stream = file.openStream()) {
            byte[] buffer = new byte[CHUNK_SIZE];
            int bytesRead;
            while (!gate.isCancelled() && (bytesRead = stream.read(buffer)) != -1) {
                gate.awaitReady();
                if (gate.isCancelled()) {
                    return;
                }

                DataChunk.Builder builder = DataChunk.newBuilder()
                        .setData(ByteString.copyFrom(buffer, 0, bytesRead));
                if (firstChunk) {
                    header(builder, file, totalSize);
                    firstChunk = false;
                }
                observer.onNext(builder.build());
            }
        }

        if (gate.isCancelled()) {
            return;
        }

        // A file with no bytes still has to say what it is. Without this the reader is handed a
        // stream with no name and no size, and has to guess both from what it asked for.
        if (firstChunk) {
            observer.onNext(header(DataChunk.newBuilder(), file, totalSize).build());
        }

        observer.onCompleted();
    }
}
