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

package cafe.jeffrey.hub.stub.grpc;

import cafe.jeffrey.hub.api.v1.DataChunk;
import cafe.jeffrey.hub.api.v1.DownloadFileRequest;
import cafe.jeffrey.hub.api.v1.FileDownloadServiceGrpc;
import cafe.jeffrey.hub.stub.data.StubDataset;
import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Optional;

/**
 * Stub {@code FileDownloadService}. One RPC for every kind of file, as on the real hub — what
 * the requested file <em>is</em> decides only what bytes come back. Every recording download
 * returns the SAME bundled JFR regardless of the requested session or file id: a session lists
 * several JFR chunks and the client fetches each of them separately, so a session of three
 * chunks arrives as three copies of this one file. Artifacts (heap dumps, logs, …) are served
 * as EMPTY files — the stub has no real ones, but the Download Assistant fetches every file of
 * a session, so they must complete rather than error.
 *
 * <p>Bytes are streamed in fixed-size {@link DataChunk}s, mirroring the real hub's wire
 * contract: {@code total_size} and the file's name on the first chunk only, and a file with no
 * bytes still sends that one header chunk, because the receiver writes what the hub named
 * rather than what it asked for.
 */
public class StubFileDownloadService extends FileDownloadServiceGrpc.FileDownloadServiceImplBase {

    private static final String RECORDING_RESOURCE = "jeffrey-persons-direct-serde-cpu.jfr.lz4";
    private static final int CHUNK_SIZE = 64 * 1024;
    private static final byte[] EMPTY_ARTIFACT = new byte[0];

    private final StubDataset dataset;
    private final byte[] recording;

    public StubFileDownloadService(StubDataset dataset) {
        this.dataset = dataset;
        this.recording = loadRecording();
    }

    @Override
    public void downloadFile(DownloadFileRequest request, StreamObserver<DataChunk> responseObserver) {
        Optional<StubDataset.Session> session = dataset.session(request.getSessionId());
        if (session.isEmpty()) {
            responseObserver.onError(
                    StubGrpcExceptions.notFound("Session not found: " + request.getSessionId()));
            return;
        }

        Optional<StubDataset.File> file = session.get().files().stream()
                .filter(candidate -> candidate.id().equals(request.getFileId()))
                .findFirst();
        if (file.isEmpty()) {
            responseObserver.onError(StubGrpcExceptions.notFound(
                    "Session " + request.getSessionId() + " holds no file with id " + request.getFileId()));
            return;
        }

        byte[] data = file.get().kind().recording() ? recording : EMPTY_ARTIFACT;
        streamBytes(file.get().name(), data, responseObserver);
    }

    private static void streamBytes(String filename, byte[] data, StreamObserver<DataChunk> responseObserver) {
        boolean firstChunk = true;
        for (int offset = 0; offset < data.length; offset += CHUNK_SIZE) {
            int length = Math.min(CHUNK_SIZE, data.length - offset);
            DataChunk.Builder chunk = DataChunk.newBuilder()
                    .setData(ByteString.copyFrom(data, offset, length));
            if (firstChunk) {
                header(chunk, filename, data.length);
                firstChunk = false;
            }
            responseObserver.onNext(chunk.build());
        }

        if (firstChunk) {
            responseObserver.onNext(header(DataChunk.newBuilder(), filename, data.length).build());
        }

        responseObserver.onCompleted();
    }

    private static DataChunk.Builder header(DataChunk.Builder builder, String filename, long totalSize) {
        return builder
                .setTotalSize(totalSize)
                .setFilename(filename);
    }

    private static byte[] loadRecording() {
        try (InputStream in = StubFileDownloadService.class.getClassLoader()
                .getResourceAsStream(RECORDING_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Stub recording not found on classpath: " + RECORDING_RESOURCE);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load stub recording: " + RECORDING_RESOURCE, e);
        }
    }
}
