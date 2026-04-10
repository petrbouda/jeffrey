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

package pbouda.jeffrey.server.core.grpc;

import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.server.api.v1.*;
import pbouda.jeffrey.server.core.manager.RepositoryManager;
import pbouda.jeffrey.server.persistence.model.SessionWithRepository;
import pbouda.jeffrey.server.persistence.repository.ServerPlatformRepositories;
import pbouda.jeffrey.shared.common.model.repository.StreamedRecordingFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

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
        try {
            RepositoryManager repoManager = repositoryManagerForSession(request.getSessionId());

            LOG.debug("Streaming merged recordings via gRPC: sessionId={} fileCount={}",
                    request.getSessionId(), request.getFileIdsList().size());

            StreamedRecordingFile recordingFile = repoManager
                    .mergeAndStreamRecordings(request.getSessionId(), request.getFileIdsList());

            streamFile(recordingFile, responseObserver);
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            LOG.error("Failed to stream merged recordings: sessionId={}", request.getSessionId(), e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void downloadArtifactFile(DownloadArtifactFileRequest request, StreamObserver<DataChunk> responseObserver) {
        try {
            RepositoryManager repoManager = repositoryManagerForSession(request.getSessionId());

            LOG.debug("Streaming artifact file via gRPC: sessionId={} fileId={}",
                    request.getSessionId(), request.getFileId());

            StreamedRecordingFile file = repoManager
                    .streamArtifactFile(request.getSessionId(), request.getFileId());

            streamFile(file, responseObserver);
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            LOG.error("Failed to stream artifact file: sessionId={} fileId={}",
                    request.getSessionId(), request.getFileId(), e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void downloadRecordingFile(DownloadRecordingFileRequest request, StreamObserver<DataChunk> responseObserver) {
        try {
            RepositoryManager repoManager = repositoryManagerForSession(request.getSessionId());

            LOG.debug("Streaming recording file via gRPC: sessionId={} fileId={}",
                    request.getSessionId(), request.getFileId());

            StreamedRecordingFile file = repoManager
                    .streamRecordingFile(request.getSessionId(), request.getFileId());

            streamFile(file, responseObserver);
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            LOG.error("Failed to stream recording file: sessionId={} fileId={}",
                    request.getSessionId(), request.getFileId(), e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    private RepositoryManager repositoryManagerForSession(String sessionId) {
        SessionWithRepository session = platformRepositories.findSessionWithRepositoryById(sessionId)
                .orElseThrow(() -> GrpcExceptions.notFound("Session not found: " + sessionId));
        return repositoryManagerFactory.apply(session.projectInfo());
    }

    private static void streamFile(StreamedRecordingFile recordingFile, StreamObserver<DataChunk> responseObserver)
            throws IOException {

        long totalSize = Files.size(recordingFile.path());

        try (InputStream stream = recordingFile.openStream()) {
            byte[] buffer = new byte[CHUNK_SIZE];
            int bytesRead;
            while ((bytesRead = stream.read(buffer)) != -1) {
                DataChunk chunk = DataChunk.newBuilder()
                        .setData(ByteString.copyFrom(buffer, 0, bytesRead))
                        .setTotalSize(totalSize)
                        .build();
                responseObserver.onNext(chunk);
            }
        }

        responseObserver.onCompleted();
    }
}
