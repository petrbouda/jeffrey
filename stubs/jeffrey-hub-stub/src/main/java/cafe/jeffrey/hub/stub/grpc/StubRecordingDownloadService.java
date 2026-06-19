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

package cafe.jeffrey.hub.stub.grpc;

import cafe.jeffrey.hub.api.v1.DataChunk;
import cafe.jeffrey.hub.api.v1.DownloadArtifactFileRequest;
import cafe.jeffrey.hub.api.v1.DownloadMergedRecordingsRequest;
import cafe.jeffrey.hub.api.v1.DownloadRecordingFileRequest;
import cafe.jeffrey.hub.api.v1.RecordingDownloadServiceGrpc;
import cafe.jeffrey.hub.stub.data.StubDataset;
import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Stub {@code RecordingDownloadService}. Recording downloads (merged and single-file) always
 * return the SAME bundled JFR regardless of the requested session or file ids — a session may
 * list several JFR chunks, but the downloaded recording is always this one file. Artifact
 * downloads (heap dumps, logs, …) are served as EMPTY files: the stub has no real artifacts,
 * but the Download Assistant fetches every source of a session (1 merged recording + N
 * artifacts), so the artifacts must complete instead of erroring.
 *
 * <p>Bytes are streamed in fixed-size {@link DataChunk}s, mirroring the real hub's wire
 * contract ({@code total_size} on the first chunk only; an empty file sends zero chunks). The
 * downloaded filename is built entirely client-side by the microscope, so no header is sent.
 */
public class StubRecordingDownloadService extends RecordingDownloadServiceGrpc.RecordingDownloadServiceImplBase {

    private static final String MERGED_RECORDING_RESOURCE = "jeffrey-persons-direct-serde-cpu.jfr.lz4";
    private static final int CHUNK_SIZE = 64 * 1024;
    private static final byte[] EMPTY_ARTIFACT = new byte[0];

    private final StubDataset dataset;
    private final byte[] mergedRecording;

    public StubRecordingDownloadService(StubDataset dataset) {
        this.dataset = dataset;
        this.mergedRecording = loadMergedRecording();
    }

    @Override
    public void downloadMergedRecordings(
            DownloadMergedRecordingsRequest request, StreamObserver<DataChunk> responseObserver) {
        streamForSession(request.getSessionId(), mergedRecording, responseObserver);
    }

    @Override
    public void downloadRecordingFile(
            DownloadRecordingFileRequest request, StreamObserver<DataChunk> responseObserver) {
        streamForSession(request.getSessionId(), mergedRecording, responseObserver);
    }

    @Override
    public void downloadArtifactFile(
            DownloadArtifactFileRequest request, StreamObserver<DataChunk> responseObserver) {
        streamForSession(request.getSessionId(), EMPTY_ARTIFACT, responseObserver);
    }

    private void streamForSession(String sessionId, byte[] data, StreamObserver<DataChunk> responseObserver) {
        dataset.session(sessionId)
                .ifPresentOrElse(
                        session -> streamBytes(data, responseObserver),
                        () -> responseObserver.onError(
                                StubGrpcExceptions.notFound("Session not found: " + sessionId)));
    }

    private static void streamBytes(byte[] data, StreamObserver<DataChunk> responseObserver) {
        boolean firstChunk = true;
        for (int offset = 0; offset < data.length; offset += CHUNK_SIZE) {
            int length = Math.min(CHUNK_SIZE, data.length - offset);
            DataChunk.Builder chunk = DataChunk.newBuilder()
                    .setData(ByteString.copyFrom(data, offset, length));
            if (firstChunk) {
                chunk.setTotalSize(data.length);
                firstChunk = false;
            }
            responseObserver.onNext(chunk.build());
        }
        responseObserver.onCompleted();
    }

    private static byte[] loadMergedRecording() {
        try (InputStream in = StubRecordingDownloadService.class.getClassLoader()
                .getResourceAsStream(MERGED_RECORDING_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Stub merged recording not found on classpath: " + MERGED_RECORDING_RESOURCE);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load stub merged recording: " + MERGED_RECORDING_RESOURCE, e);
        }
    }
}
