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

package cafe.jeffrey.server.core.grpc;

import com.google.protobuf.ByteString;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.server.api.v1.*;
import cafe.jeffrey.server.core.manager.RepositoryManager;
import cafe.jeffrey.server.persistence.api.SessionWithRepository;
import cafe.jeffrey.server.persistence.api.ServerPlatformRepositories;
import cafe.jeffrey.shared.common.Schedulers;
import cafe.jeffrey.shared.common.model.repository.StreamedRecordingFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.concurrent.Semaphore;

public class RecordingDownloadGrpcService extends RecordingDownloadServiceGrpc.RecordingDownloadServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(RecordingDownloadGrpcService.class);
    private static final int CHUNK_SIZE = 64 * 1024; // 64KB

    private final ServerPlatformRepositories platformRepositories;
    private final RepositoryManager.Factory repositoryManagerFactory;

    public RecordingDownloadGrpcService(
            ServerPlatformRepositories platformRepositories,
            RepositoryManager.Factory repositoryManagerFactory) {

        this.platformRepositories = platformRepositories;
        this.repositoryManagerFactory = repositoryManagerFactory;
    }

    @Override
    public void downloadMergedRecordings(DownloadMergedRecordingsRequest request, StreamObserver<DataChunk> responseObserver) {
        // ReadyGate.attach must run in the gRPC handler thread (before this method returns) — gRPC rejects
        // setOnReadyHandler / setOnCancelHandler once the StreamObserver has been handed back.
        ServerCallStreamObserver<DataChunk> observer = (ServerCallStreamObserver<DataChunk>) responseObserver;
        ReadyGate gate = ReadyGate.attach(observer);

        Schedulers.streamingExecutor().execute(() -> {
            try {
                RepositoryManager repoManager = repositoryManagerForSession(request.getSessionId());

                LOG.debug("Streaming merged recordings via gRPC: sessionId={} fileCount={}",
                        request.getSessionId(), request.getFileIdsList().size());

                StreamedRecordingFile recordingFile = repoManager
                        .mergeAndStreamRecordings(request.getSessionId(), request.getFileIdsList());

                streamWithBackpressure(recordingFile, observer, gate);
            } catch (StatusRuntimeException e) {
                observer.onError(e);
            } catch (Exception e) {
                LOG.error("Failed to stream merged recordings: sessionId={}", request.getSessionId(), e);
                observer.onError(GrpcExceptions.internal(e));
            }
        });
    }

    @Override
    public void downloadArtifactFile(DownloadArtifactFileRequest request, StreamObserver<DataChunk> responseObserver) {
        ServerCallStreamObserver<DataChunk> observer = (ServerCallStreamObserver<DataChunk>) responseObserver;
        ReadyGate gate = ReadyGate.attach(observer);

        Schedulers.streamingExecutor().execute(() -> {
            try {
                RepositoryManager repoManager = repositoryManagerForSession(request.getSessionId());

                LOG.debug("Streaming artifact file via gRPC: sessionId={} fileId={}",
                        request.getSessionId(), request.getFileId());

                StreamedRecordingFile file = repoManager
                        .streamArtifactFile(request.getSessionId(), request.getFileId());

                streamWithBackpressure(file, observer, gate);
            } catch (StatusRuntimeException e) {
                observer.onError(e);
            } catch (Exception e) {
                LOG.error("Failed to stream artifact file: sessionId={} fileId={}",
                        request.getSessionId(), request.getFileId(), e);
                observer.onError(GrpcExceptions.internal(e));
            }
        });
    }

    @Override
    public void downloadRecordingFile(DownloadRecordingFileRequest request, StreamObserver<DataChunk> responseObserver) {
        ServerCallStreamObserver<DataChunk> observer = (ServerCallStreamObserver<DataChunk>) responseObserver;
        ReadyGate gate = ReadyGate.attach(observer);

        Schedulers.streamingExecutor().execute(() -> {
            try {
                RepositoryManager repoManager = repositoryManagerForSession(request.getSessionId());

                LOG.debug("Streaming recording file via gRPC: sessionId={} fileId={}",
                        request.getSessionId(), request.getFileId());

                StreamedRecordingFile file = repoManager
                        .streamRecordingFile(request.getSessionId(), request.getFileId());

                streamWithBackpressure(file, observer, gate);
            } catch (StatusRuntimeException e) {
                observer.onError(e);
            } catch (Exception e) {
                LOG.error("Failed to stream recording file: sessionId={} fileId={}",
                        request.getSessionId(), request.getFileId(), e);
                observer.onError(GrpcExceptions.internal(e));
            }
        });
    }

    private RepositoryManager repositoryManagerForSession(String sessionId) {
        SessionWithRepository session = platformRepositories.findSessionWithRepositoryById(sessionId)
                .orElseThrow(() -> GrpcExceptions.notFound("Session not found: " + sessionId));
        return repositoryManagerFactory.apply(session.projectInfo());
    }

    private static void streamWithBackpressure(
            StreamedRecordingFile recordingFile,
            ServerCallStreamObserver<DataChunk> observer,
            ReadyGate gate) throws IOException, InterruptedException {

        long totalSize = Files.size(recordingFile.path());
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
                    builder.setTotalSize(totalSize);
                    firstChunk = false;
                }
                observer.onNext(builder.build());
            }
        }

        if (!gate.isCancelled()) {
            observer.onCompleted();
        }
    }

    /**
     * Backpressure gate for a server-streaming call. Pauses the producer when the
     * channel is not ready, resumes when gRPC fires onReady, and short-circuits when
     * the call is cancelled by the client.
     */
    private static final class ReadyGate {

        private final ServerCallStreamObserver<?> observer;
        private final Semaphore permits = new Semaphore(0);
        private volatile boolean cancelled = false;

        private ReadyGate(ServerCallStreamObserver<?> observer) {
            this.observer = observer;
        }

        static ReadyGate attach(ServerCallStreamObserver<?> observer) {
            ReadyGate gate = new ReadyGate(observer);
            observer.setOnReadyHandler(gate.permits::release);
            observer.setOnCancelHandler(() -> {
                gate.cancelled = true;
                gate.permits.release();
            });
            return gate;
        }

        boolean isCancelled() {
            return cancelled || observer.isCancelled();
        }

        void awaitReady() throws InterruptedException {
            if (observer.isReady() || isCancelled()) {
                return;
            }
            permits.drainPermits();
            while (!observer.isReady() && !isCancelled()) {
                permits.acquire();
            }
        }
    }
}
