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
import cafe.jeffrey.hub.api.v1.DownloadFileRequest;
import cafe.jeffrey.hub.api.v1.FileDownloadServiceGrpc;
import cafe.jeffrey.hub.stub.data.StubDataset;
import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * The stub's one download path, like the hub's: a compressed-chunk row streams the SAME bundled recording
 * regardless of the session or file id, every other row an empty body — the stub has no real
 * logs or dumps, but Microscope fetches every file of a session, so they must complete rather
 * than error. The hub never merges, so neither does the stub.
 *
 * <p>Bytes are streamed in fixed-size {@link DataChunk}s, mirroring the real hub's wire
 * format so a client exercises the same chunk reassembly it would against a real hub.
 */
public class StubFileDownloadService extends FileDownloadServiceGrpc.FileDownloadServiceImplBase {

    private static final String RECORDING_RESOURCE = "jeffrey-persons-direct-serde-cpu.jfr.lz4";
    private static final int CHUNK_SIZE = 64 * 1024;
    private static final byte[] EMPTY_FILE = new byte[0];

    private final StubDataset dataset;
    private final byte[] recording;

    public StubFileDownloadService(StubDataset dataset) {
        this.dataset = dataset;
        this.recording = loadRecording();
    }

    @Override
    public void downloadFile(DownloadFileRequest request, StreamObserver<DataChunk> responseObserver) {
        dataset.session(request.getSessionId())
                .ifPresentOrElse(
                        session -> streamBytes(bytesOf(session, request.getFileId()), responseObserver),
                        () -> responseObserver.onError(
                                StubGrpcExceptions.notFound("Session not found: " + request.getSessionId())));
    }

    private byte[] bytesOf(StubDataset.Session session, String fileId) {
        return session.files().stream()
                .filter(file -> file.id().equals(fileId))
                .findFirst()
                .map(file -> file.kind() == StubDataset.FileKind.JFR_LZ4 ? recording : EMPTY_FILE)
                .orElse(EMPTY_FILE);
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
