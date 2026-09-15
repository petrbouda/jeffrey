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
import cafe.jeffrey.shared.common.model.repository.ChunkWindow;
import cafe.jeffrey.shared.common.model.repository.RecordingSession;
import cafe.jeffrey.shared.common.model.repository.RepositoryFile;
import cafe.jeffrey.shared.common.model.repository.StreamedRecordingFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class RecordingDownloadGrpcService extends RecordingDownloadServiceGrpc.RecordingDownloadServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(RecordingDownloadGrpcService.class);
    private static final int CHUNK_SIZE = 64 * 1024; // 64KB

    private final GrpcLookups lookups;

    public RecordingDownloadGrpcService(GrpcLookups lookups) {
        this.lookups = lookups;
    }

    @Override
    public void downloadMergedRecordings(DownloadMergedRecordingsRequest request, StreamObserver<DataChunk> responseObserver) {
        streamDownload(responseObserver, "merged recordings: sessionId=" + request.getSessionId(), () -> {
            RepositoryManager repoManager = lookups.repositoryManagerForSession(request.getSessionId());

            LOG.debug("Streaming merged recordings via gRPC: sessionId={} fileCount={}",
                    request.getSessionId(), request.getFileIdsList().size());

            List<String> fileIds = validatedFileIds(repoManager, request);
            return repoManager.mergeAndStreamRecordings(request.getSessionId(), fileIds);
        });
    }

    /**
     * The chunks this request may merge, or a refusal saying why it may not.
     *
     * <p>This is the authority on what a merged recording is allowed to be, because merging is
     * concatenation: the chunks are decompressed into one stream one after another, and nothing in
     * the result records that a chunk was skipped. A recording built from chunks 1 and 3 claims the
     * span of 1 to 3 while holding two thirds of it, and every rate read off it is wrong by the
     * size of the hole. So the run has to be unbroken, judged over the chunks the session lists
     * now — a chunk deleted from the session leaves its neighbours genuinely adjacent, and nothing
     * here can know about a file that is no longer there.
     *
     * <p>Two older silences go with it. An id the session does not hold was dropped by the merge's
     * own {@code contains} filter, so a request naming a mistyped chunk quietly got a recording
     * made of the rest; and an <em>empty</em> list matched nothing rather than everything, because
     * the filter tests for {@code null} and protobuf hands back {@code []} — which is the opposite
     * of what this RPC's own documentation promises. Both are answered here instead.
     *
     * <p>Thrown as a {@link StatusRuntimeException} rather than an
     * {@code IllegalArgumentException}, because {@link #streamDownload} passes only the former
     * through and reports anything else as {@code INTERNAL}.
     */
    private static List<String> validatedFileIds(
            RepositoryManager repoManager, DownloadMergedRecordingsRequest request) {

        String sessionId = request.getSessionId();
        List<String> requested = request.getFileIdsList();
        RecordingSession session = repoManager.findRecordingSessions(sessionId)
                .orElseThrow(() -> GrpcExceptions.notFound("Session not found: " + sessionId));
        List<RepositoryFile> files = session.files() == null ? List.of() : session.files();

        // Empty means every finished recording file, as this service's contract says it does.
        if (requested.isEmpty()) {
            return List.of();
        }

        Set<String> known = files.stream().map(RepositoryFile::id).collect(Collectors.toSet());
        List<String> unknown = requested.stream().filter(id -> !known.contains(id)).toList();
        if (!unknown.isEmpty()) {
            throw GrpcExceptions.invalidArgument(
                    "Session " + sessionId + " has no file " + unknown + ". Merging would have silently "
                            + "left it out and returned a recording made of the rest.");
        }

        ChunkWindow.Selection selection = ChunkWindow.ofFiles(files, Set.copyOf(requested), session.finishedAt());
        if (!selection.contiguous()) {
            throw GrpcExceptions.invalidArgument(
                    "The recording files chosen from session " + sessionId + " are not next to each other: "
                            + selection.describeGap(files) + " lies between them. Merging concatenates the "
                            + "files into one recording, so they have to be an unbroken run.");
        }
        return requested;
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

}
