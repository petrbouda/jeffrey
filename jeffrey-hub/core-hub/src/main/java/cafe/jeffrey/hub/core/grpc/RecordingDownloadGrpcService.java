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
import cafe.jeffrey.hub.api.v1.*;
import cafe.jeffrey.hub.core.manager.RepositoryManager;
import cafe.jeffrey.shared.common.Schedulers;
import cafe.jeffrey.shared.common.filesystem.FileSizeReader;
import cafe.jeffrey.shared.common.model.repository.StreamedRecordingFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Supplier;

public class RecordingDownloadGrpcService extends RecordingDownloadServiceGrpc.RecordingDownloadServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(RecordingDownloadGrpcService.class);
    private static final int CHUNK_SIZE = 64 * 1024; // 64KB

    private final GrpcLookups lookups;

    public RecordingDownloadGrpcService(GrpcLookups lookups) {
        this.lookups = lookups;
    }

    @Override
    public void downloadArtifactFile(DownloadArtifactFileRequest request, StreamObserver<DataChunk> responseObserver) {
        streamDownload(responseObserver, "artifact file: sessionId=" + request.getSessionId() + " fileId=" + request.getFileId(), () -> {
            RepositoryManager repoManager = lookups.repositoryManagerForSession(request.getSessionId());

            LOG.debug("Streaming artifact file via gRPC: sessionId={} fileId={}",
                    request.getSessionId(), request.getFileId());

            return repoManager.streamArtifactFile(request.getSessionId(), request.getFileId());
        });
    }

    @Override
    public void downloadRecordingFile(DownloadRecordingFileRequest request, StreamObserver<DataChunk> responseObserver) {
        streamDownload(responseObserver, "recording file: sessionId=" + request.getSessionId() + " fileId=" + request.getFileId(), () -> {
            RepositoryManager repoManager = lookups.repositoryManagerForSession(request.getSessionId());

            LOG.debug("Streaming recording file via gRPC: sessionId={} fileId={}",
                    request.getSessionId(), request.getFileId());

            return repoManager.streamRecordingFile(request.getSessionId(), request.getFileId());
        });
    }

    /**
     * Runs a server-streaming download: attaches the backpressure gate on the gRPC handler thread,
     * then on the streaming executor resolves the file via {@code producer} and pumps it with
     * backpressure. A {@link StatusRuntimeException} from the producer (e.g. a NOT_FOUND lookup)
     * passes through unchanged; any other failure is logged with {@code errorContext} and reported
     * as {@code INTERNAL}.
     */
    private static void streamDownload(
            StreamObserver<DataChunk> responseObserver,
            String errorContext,
            Supplier<StreamedRecordingFile> producer) {

        // ReadyGate.attach must run in the gRPC handler thread (before this method returns) — gRPC rejects
        // setOnReadyHandler / setOnCancelHandler once the StreamObserver has been handed back.
        ServerCallStreamObserver<DataChunk> observer = (ServerCallStreamObserver<DataChunk>) responseObserver;
        ReadyGate gate = ReadyGate.attach(observer);

        Schedulers.streamingExecutor().execute(() -> {
            try {
                streamWithBackpressure(producer.get(), observer, gate);
            } catch (StatusRuntimeException e) {
                observer.onError(e);
            } catch (Exception e) {
                LOG.error("Failed to stream {}", errorContext, e);
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
            DataChunk.Builder builder, StreamedRecordingFile recordingFile, long totalSize) {

        return builder
                .setTotalSize(totalSize)
                .setFilename(recordingFile.fileName());
    }

    private static void streamWithBackpressure(
            StreamedRecordingFile recordingFile,
            ServerCallStreamObserver<DataChunk> observer,
            ReadyGate gate) throws IOException, InterruptedException {

        long totalSize = FileSizeReader.OPEN_HANDLE.size(recordingFile.path());
        boolean firstChunk = true;

        try (InputStream stream = recordingFile.openStream()) {
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
                    header(builder, recordingFile, totalSize);
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
            observer.onNext(header(DataChunk.newBuilder(), recordingFile, totalSize).build());
        }

        observer.onCompleted();
    }

}
